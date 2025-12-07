#!/usr/bin/env bash
# Reboot regression smoke for overlay reliability.
# Scenarios:
#   1) no_lock         : reboot with lock off
#   2) lock_immediate  : lock -> reboot within 5s
#   3) lock_30s        : lock -> wait ~30s -> reboot
#   4) lock_3m         : lock -> wait ~3m  -> reboot
#   5) lock_10m        : lock -> wait ~9m  -> reboot (long tail recovery)
# Usage examples:
#   bash tools/reboot_scenarios.sh all
#   bash tools/reboot_scenarios.sh lock_30s

set -euo pipefail

APP_ID="com.example.smartphone_lock"
LOG_FILTER="smartphone_lock|BootFastStartupReceiver|BootCompletedReceiver|LockMonitorService|OverlayLockService|WatchdogScheduler|WatchdogWorkScheduler|WatchdogWorker|TestControlReceiver|ActivityManager"
PREF_PATH="/data/user_de/0/${APP_ID}/shared_prefs/direct_boot_lock_state.xml"

SCENARIOS=("no_lock" "lock_immediate" "lock_30s" "lock_3m" "lock_10m")

SERIAL="${ANDROID_SERIAL:-}"
# ロック時間（分）。デフォルトを 20 分に延長（10 分シナリオでの失効防止）。必要なら環境変数で上書き。
LOCK_MINUTES="${LOCK_MINUTES:-20}"

# 自動でロック開始する（手動操作なし）。0 にすると従来の手動プロンプトに戻る。
AUTO_LOCK="${AUTO_LOCK:-1}"

pick_serial() {
  if [[ -n "$SERIAL" ]]; then
    return
  fi
  local devices
  devices=$(adb devices | awk 'NR>1 && $2=="device"{print $1}')
  local count
  count=$(echo "$devices" | grep -c "." || true)
  if [[ $count -eq 1 ]]; then
    SERIAL=$(echo "$devices" | head -1)
    info "Using device: $SERIAL"
  else
    echo "[ERROR] 複数デバイス/エミュレータが接続されています。--serial もしくは ANDROID_SERIAL を指定してください。候補:"
    adb devices
    exit 1
  fi
}

adb_cmd() {
  adb -s "$SERIAL" "$@"
}

info() { echo "[INFO] $*"; }
warn() { echo "[WARN] $*"; }

wait_for_boot() {
  adb_cmd wait-for-device >/dev/null
  # Wait for sys.boot_completed=1
  for _ in {1..40}; do
    if adb_cmd shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; then
      sleep 2
      return 0
    fi
    sleep 1
  done
  warn "boot_completed not observed; continuing anyway"
}

read_lock_state() {
  if ! adb_cmd shell run-as "${APP_ID}" ls "${PREF_PATH}" >/dev/null 2>&1; then
    warn "run-as cannot access DP prefs (likely user locked)."
    return
  fi
  adb_cmd shell run-as "${APP_ID}" cat "${PREF_PATH}" 2>/dev/null || true
}

collect_logs() {
  local outfile="$1"
  adb_cmd logcat -d >"${outfile}"
  grep -E "${LOG_FILTER}" "${outfile}" || true
  adb_cmd logcat -c || true
}

evaluate_logs() {
  local outfile="$1"
  local expect_lock="$2" # 1 = locked expected, 0 = unlocked expected

  local has_start has_schedule has_cancel
  has_start=$(grep -m1 "LockMonitorService: onStartCommand" "${outfile}" || true)
  has_schedule=$(grep -m1 "WatchdogWorkScheduler: Schedule WorkManager heartbeat" "${outfile}" || true)
  has_cancel=$(grep -m1 "WatchdogWorkScheduler: Cancel WorkManager heartbeat" "${outfile}" || true)

  if [[ "${expect_lock}" == "1" ]]; then
    if [[ -n "${has_start}" ]]; then
      info "Verdict: PASS (lock active: service restart observed)"
    elif [[ -n "${has_schedule}" ]]; then
      info "Verdict: WARN (heartbeat scheduled but service start not seen)"
    else
      warn "Verdict: FAIL? expected lock active but missing service/logs"
    fi
  else
    if [[ -z "${has_schedule}" || -n "${has_cancel}" ]]; then
      info "Verdict: PASS (no-lock: heartbeat not scheduled or canceled)"
    else
      warn "Verdict: FAIL? unexpected heartbeat schedule in no-lock scenario"
    fi
  fi
}

send_test_status() {
  info "Post-boot TEST_STATUS broadcast"
  adb_cmd logcat -c || true
  adb_cmd shell am broadcast \
    -n com.example.smartphone_lock/.receiver.TestControlReceiver \
    -a com.example.smartphone_lock.action.TEST_STATUS >/dev/null || \
    warn "TEST_STATUS broadcast failed (is debug build installed?)"
  sleep 1
  adb_cmd logcat -d | grep -E "TestControlReceiver|${APP_ID}" || true
  adb_cmd logcat -c || true
}

reboot_and_wait() {
  info "Rebooting device..."
  adb_cmd reboot
  wait_for_boot
  info "Device back online."
}

start_lock_auto() {
  info "Trigger TEST_LOCK (duration=${LOCK_MINUTES}m)"
  adb_cmd shell am broadcast \
    -n com.example.smartphone_lock/.receiver.TestControlReceiver \
    -a com.example.smartphone_lock.action.TEST_LOCK \
    --el extra_duration_minutes "$LOCK_MINUTES" >/dev/null || \
    warn "TEST_LOCK broadcast may have failed (check app installed & debug build)"
}

prompt_lock_if_needed() {
  local label="$1"
  if [[ "$AUTO_LOCK" == "1" ]]; then
    start_lock_auto
  else
    info "Prepare scenario: ${label}"
    info "ロックを開始して Enter を押してください。"
    read -r _
  fi
}

scenario_no_lock() {
  info "Scenario: no_lock"
  local tmp
  tmp=$(mktemp)
  collect_logs "${tmp}"
  reboot_and_wait
  info "Post-boot lock state (DP):"
  read_lock_state
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 0
  send_test_status
}

scenario_lock_immediate() {
  info "Scenario: lock_immediate"
  local tmp
  tmp=$(mktemp)
  collect_logs "${tmp}"
  prompt_lock_if_needed "start lock now (reboot in 5s)"
  sleep 5
  reboot_and_wait
  info "Post-boot lock state (DP):"
  read_lock_state
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 1
  send_test_status
}

scenario_lock_30s() {
  info "Scenario: lock_30s"
  local tmp
  tmp=$(mktemp)
  collect_logs "${tmp}"
  prompt_lock_if_needed "start lock now (reboot after ~30s)"
  sleep 30
  reboot_and_wait
  info "Post-boot lock state (DP):"
  read_lock_state
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 1
  send_test_status
}

scenario_lock_3m() {
  info "Scenario: lock_3m"
  local tmp
  tmp=$(mktemp)
  collect_logs "${tmp}"
  prompt_lock_if_needed "start lock now (reboot after ~180s)"
  sleep 180
  reboot_and_wait
  info "Post-boot lock state (DP):"
  read_lock_state
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 1
  send_test_status
}

scenario_lock_10m() {
  info "Scenario: lock_10m"
  local tmp
  tmp=$(mktemp)
  collect_logs "${tmp}"
  prompt_lock_if_needed "start lock now (reboot after ~600s)"
  # 9 分待機に短縮し、デフォルト 20 分ロックと組み合わせて失効を防ぐ
  sleep 540
  reboot_and_wait
  info "Post-boot lock state (DP):"
  read_lock_state
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 1
  send_test_status
}

run() {
  local target="$1"
  pick_serial
  case "$target" in
    no_lock) scenario_no_lock ;;
    lock_immediate) scenario_lock_immediate ;;
    lock_30s) scenario_lock_30s ;;
    lock_3m) scenario_lock_3m ;;
    lock_10m) scenario_lock_10m ;;
    all)
      for s in "${SCENARIOS[@]}"; do
        run "$s"
      done
      ;;
    *)
      echo "Usage: $0 {all|no_lock|lock_immediate|lock_30s|lock_3m|lock_10m}" >&2
      exit 1
      ;;
  esac
}

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 {all|no_lock|lock_immediate|lock_30s|lock_3m|lock_10m}" >&2
  exit 1
fi

run "$1"
