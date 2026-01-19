#!/usr/bin/env bash
# Reboot regression smoke for overlay reliability.
# Scenarios:
#   1) no_lock         : reboot with lock off
#   2) lock_immediate  : lock -> reboot within 5s
#   3) lock_30s        : lock -> wait ~30s -> reboot
#   4) lock_3m         : lock -> wait ~3m  -> reboot
#   5) lock_10m        : lock -> wait ~9m  -> reboot (long tail recovery)
#   6) lock_60m        : lock -> wait ~60m -> reboot
#   7) lock_60m_no_reboot : lock -> wait ~60m -> check without reboot
#   8) lock_90m        : lock -> wait ~90m -> reboot
#   9) lock_120m       : lock -> wait ~120m -> reboot
#  10) screen_off_long : lock -> screen off -> wait -> check without reboot
#  11) battery_saver_on: lock -> enable battery saver -> wait -> reboot
#  12) time_shift      : lock -> shift time forward/back -> check
#  13) dp_corrupt_missing_end : lock -> remove lock_end_timestamp -> reboot
#  14) cold_boot       : lock -> emulator power off -> cold boot -> check
#  15) power_off_boot  : lock -> (manual) power off -> boot -> check
#  16) lock_end_before : lock -> wait near end -> reboot (expect restore)
#  17) lock_end_after  : lock already ended -> reboot (expect no restore)
#  18) my_package_replaced : reinstall debug build (expect restore)
# Usage examples:
#   bash tools/reboot_scenarios.sh all
#   bash tools/reboot_scenarios.sh lock_30s

set -euo pipefail

APP_ID="${APP_ID:-jp.kawai.phonefasting}"
ACTION_PREFIX="${ACTION_PREFIX:-jp.kawai.ultrafocus}"
RECEIVER_CLASS="${RECEIVER_CLASS:-jp.kawai.ultrafocus.receiver.TestControlReceiver}"
LOG_FILTER="BootFastStartupReceiver|BootCompletedReceiver|LockMonitorService|OverlayLockService|WatchdogScheduler|WatchdogWorkScheduler|WatchdogWorker"
PREF_PATH="/data/user_de/0/${APP_ID}/shared_prefs/direct_boot_lock_state.xml"

SCENARIOS=("no_lock" "lock_immediate" "lock_30s" "lock_3m" "lock_10m" "lock_60m" "lock_60m_no_reboot" "lock_90m" "lock_120m" "screen_off_long" "battery_saver_on" "time_shift" "dp_corrupt_missing_end" "cold_boot" "power_off_boot" "lock_end_before" "lock_end_after" "my_package_replaced")

SERIAL="${ANDROID_SERIAL:-}"
# ロック時間（分）。デフォルトを 20 分に延長（10 分シナリオでの失効防止）。必要なら環境変数で上書き。
LOCK_MINUTES="${LOCK_MINUTES:-20}"
# 長時間シナリオ向けロック時間（分）
LOCK_MINUTES_LONG="${LOCK_MINUTES_LONG:-150}"
# 60分ノーリブート用ロック時間（分）
LOCK_MINUTES_60M_NO_REBOOT="${LOCK_MINUTES_60M_NO_REBOOT:-65}"
# 画面消灯・省電力シナリオ向けロック時間（分）
LOCK_MINUTES_SCREEN_OFF="${LOCK_MINUTES_SCREEN_OFF:-70}"
LOCK_MINUTES_BATTERY_SAVER="${LOCK_MINUTES_BATTERY_SAVER:-45}"
# 90分・120分シナリオ向けロック時間（分）
LOCK_MINUTES_90M="${LOCK_MINUTES_90M:-150}"
LOCK_MINUTES_120M="${LOCK_MINUTES_120M:-180}"

# ブート後にログ収集するまでの待機秒数（Boot レシーバ/再試行を拾う）。必要なら環境変数で上書き。
POST_BOOT_WAIT_SECONDS="${POST_BOOT_WAIT_SECONDS:-35}"
# TestControlReceiver 発火後にサービス起動ログが出揃うまでの追加待機秒数
POST_BOOT_SERVICE_WAIT_SECONDS="${POST_BOOT_SERVICE_WAIT_SECONDS:-3}"

# 自動でロック開始する（手動操作なし）。0 にすると従来の手動プロンプトに戻る。
AUTO_LOCK="${AUTO_LOCK:-1}"

# lock_end境界テスト用（終了時刻のオフセットと待機）
LOCK_END_BEFORE_OFFSET_SECONDS="${LOCK_END_BEFORE_OFFSET_SECONDS:-300}"   # 終了は今+300s
LOCK_END_BEFORE_TARGET_SECONDS="${LOCK_END_BEFORE_TARGET_SECONDS:-120}"   # 残り約120sで再起動（ブート時間バッファ）
LOCK_END_AFTER_OFFSET_SECONDS="${LOCK_END_AFTER_OFFSET_SECONDS:--30}"     # 終了は今-30s

# lock_60m 待機秒数
LOCK_60M_WAIT_SECONDS="${LOCK_60M_WAIT_SECONDS:-3600}"
# lock_90m / lock_120m 待機秒数
LOCK_90M_WAIT_SECONDS="${LOCK_90M_WAIT_SECONDS:-5400}"
LOCK_120M_WAIT_SECONDS="${LOCK_120M_WAIT_SECONDS:-7200}"

# 画面消灯・省電力・時刻変更シナリオ用
SCREEN_OFF_WAIT_SECONDS="${SCREEN_OFF_WAIT_SECONDS:-3600}"
BATTERY_SAVER_WAIT_SECONDS="${BATTERY_SAVER_WAIT_SECONDS:-1800}"
TIME_SHIFT_FORWARD_SECONDS="${TIME_SHIFT_FORWARD_SECONDS:-900}"
TIME_SHIFT_BACK_SECONDS="${TIME_SHIFT_BACK_SECONDS:-900}"
# 長時間待機を時間シフトで短縮する場合に使用
FAST_FORWARD_TIME_SHIFT="${FAST_FORWARD_TIME_SHIFT:-0}"
# emulator cold boot 用
EMULATOR_ARGS="${EMULATOR_ARGS:--no-snapshot-load -no-snapshot-save}"
# 省電力/画面OFF/長時間シナリオで Doze を強制する（0/1）
FORCE_DEVICEIDLE="${FORCE_DEVICEIDLE:-0}"
DEVICEIDLE_MODE="${DEVICEIDLE_MODE:-deep}"

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

screen_off() {
  adb_cmd shell input keyevent 223 >/dev/null 2>&1 || true
  adb_cmd shell input keyevent 26 >/dev/null 2>&1 || true
}

screen_on() {
  adb_cmd shell input keyevent 224 >/dev/null 2>&1 || true
  adb_cmd shell input keyevent 26 >/dev/null 2>&1 || true
}

set_battery_saver() {
  local enabled="$1"
  adb_cmd shell settings put global low_power "${enabled}" >/dev/null 2>&1 || true
  adb_cmd shell cmd power set-mode "${enabled}" >/dev/null 2>&1 || true
}

battery_saver_status() {
  adb_cmd shell settings get global low_power 2>/dev/null | tr -d '\r'
}

resolve_emulator_cmd() {
  if [[ -n "${EMULATOR_CMD:-}" ]]; then
    echo "${EMULATOR_CMD}"
    return 0
  fi
  if command -v emulator >/dev/null 2>&1; then
    echo "emulator"
    return 0
  fi
  local sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  if [[ -n "${sdk_root}" && -x "${sdk_root}/emulator/emulator" ]]; then
    echo "${sdk_root}/emulator/emulator"
    return 0
  fi
  return 1
}

current_avd_name() {
  adb_cmd emu avd name 2>/dev/null | tr -d '\r' | awk 'NF && $1!="OK"{print; exit}'
}

start_emulator_cold_boot() {
  local avd_name="$1"
  local emulator_cmd
  emulator_cmd="$(resolve_emulator_cmd)" || {
    warn "Cannot find emulator command. Set EMULATOR_CMD or ANDROID_SDK_ROOT."
    return 1
  }
  if [[ -z "${avd_name}" ]]; then
    warn "AVD name not detected. Set AVD_NAME env."
    return 1
  fi
  local args=()
  read -r -a args <<<"${EMULATOR_ARGS}"
  info "Starting emulator cold boot: ${avd_name}"
  nohup "${emulator_cmd}" -avd "${avd_name}" "${args[@]}" >"/tmp/emulator_${avd_name}.log" 2>&1 &
  sleep 2
}

deviceidle_force() {
  local mode="${1:-deep}"
  adb_cmd shell cmd deviceidle force-idle "${mode}" >/dev/null 2>&1 || true
}

deviceidle_unforce() {
  adb_cmd shell cmd deviceidle unforce >/dev/null 2>&1 || true
}

deviceidle_status() {
  local force deep
  force="$(adb_cmd shell cmd deviceidle get force 2>/dev/null | tr -d '\r' || true)"
  deep="$(adb_cmd shell cmd deviceidle get deep 2>/dev/null | tr -d '\r' || true)"
  echo "force=${force} deep=${deep}"
}

set_auto_time() {
  local enabled="$1"
  adb_cmd shell settings put global auto_time "${enabled}" >/dev/null 2>&1 || true
  adb_cmd shell settings put global auto_time_zone "${enabled}" >/dev/null 2>&1 || true
}

set_device_time_millis() {
  local target_ms="$1"
  adb_cmd shell cmd alarm set-time "${target_ms}" >/dev/null 2>&1
}

restore_setting() {
  local key="$1"
  local value="$2"
  if [[ -z "${value}" || "${value}" == "null" ]]; then
    return
  fi
  adb_cmd shell settings put global "${key}" "${value}" >/dev/null 2>&1 || true
}

corrupt_dp_remove_lock_end() {
  if ! adb_cmd shell run-as "${APP_ID}" ls "${PREF_PATH}" >/dev/null 2>&1; then
    warn "Cannot access DP prefs to corrupt (run-as failed)"
    return 1
  fi
  adb_cmd shell "run-as ${APP_ID} sh -c 'grep -v lock_end_timestamp ${PREF_PATH} > ${PREF_PATH}.tmp && mv ${PREF_PATH}.tmp ${PREF_PATH}'" >/dev/null 2>&1 || return 1
  return 0
}

dump_service_state() {
  adb_cmd shell dumpsys activity services "${APP_ID}" 2>/dev/null | grep -E "OverlayLockService|LockMonitorService" || true
}

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
  if has_test_receiver "${ACTION_PREFIX}.action.TEST_STATUS"; then
    send_test_status 0
    return
  fi
  if ! adb_cmd shell run-as "${APP_ID}" ls "${PREF_PATH}" >/dev/null 2>&1; then
    warn "run-as cannot access DP prefs (likely user locked)."
    return
  fi
  adb_cmd shell run-as "${APP_ID}" cat "${PREF_PATH}" 2>/dev/null || true
}

clear_dp_lock_state() {
  if unlock_via_test_receiver; then
    return
  fi
  adb_cmd shell run-as "${APP_ID}" rm -f "${PREF_PATH}" >/dev/null 2>&1 || true
}

dp_lock_active() {
  if has_test_receiver "${ACTION_PREFIX}.action.TEST_STATUS"; then
    if test_receiver_lock_active; then
      return 0
    fi
    return 1
  fi
  local xml
  xml="$(adb_cmd shell run-as "${APP_ID}" cat "${PREF_PATH}" 2>/dev/null || true)"
  if [[ -z "$xml" ]]; then
    return 1
  fi
  if ! echo "$xml" | grep -q 'name="is_locked" value="true"'; then
    return 1
  fi
  local lock_end
  lock_end=$(echo "$xml" | sed -n 's/.*name="lock_end_timestamp" value="\\([0-9]*\\)".*/\\1/p')
  if [[ -n "$lock_end" ]]; then
    local now_ms
    now_ms="$(now_millis)"
    if (( lock_end <= now_ms )); then
      return 1
    fi
  fi
  return 0
}

write_dp_lock_state() {
  local is_locked="$1"
  local lock_start="$2"
  local lock_end="$3"
  cat <<EOF | adb_cmd shell run-as "${APP_ID}" sh -c "cat > ${PREF_PATH}"
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="is_locked" value="${is_locked}" />
    <long name="lock_start_timestamp" value="${lock_start}" />
    <long name="lock_end_timestamp" value="${lock_end}" />
</map>
EOF
}

now_millis() {
  python3 - <<'PY'
import time
print(int(time.time() * 1000))
PY
}

collect_logs() {
  local outfile="$1"
  adb_cmd logcat -d -v time >"${outfile}"
  grep -E "${LOG_FILTER}" "${outfile}" || true
  adb_cmd logcat -c || true
}

evaluate_logs() {
  local outfile="$1"
  local expect_lock="$2" # 1 = locked expected, 0 = unlocked expected

  local has_monitor has_overlay has_boot has_schedule has_cancel
  has_monitor=$(grep -m1 -E "LockMonitorService.*onStartCommand|ActivityManager: Background started FGS:.*LockMonitorService" "${outfile}" || true)
  has_overlay=$(grep -m1 -E "OverlayLockService.*onStartCommand|ActivityManager: Background started FGS:.*OverlayLockService" "${outfile}" || true)
  has_boot=$(grep -m1 -E "Boot(FastStartup|Completed)Receiver:.*(locked state detected|Lock active|Fast start lock services)" "${outfile}" || true)
  has_schedule=$(grep -m1 "WatchdogWorkScheduler: Schedule WorkManager heartbeat" "${outfile}" || true)
  has_cancel=$(grep -m1 "WatchdogWorkScheduler: Cancel WorkManager heartbeat" "${outfile}" || true)

  if [[ "${expect_lock}" == "1" ]]; then
    if [[ -n "${has_monitor}" || -n "${has_overlay}" || -n "${has_boot}" ]]; then
      info "Verdict: PASS (lock active: service restart observed)"
    elif [[ -n "${has_schedule}" ]]; then
      info "Verdict: WARN (heartbeat scheduled but service start not seen)"
    else
      warn "Verdict: FAIL? expected lock active but missing service/logs"
    fi
  else
    if [[ -n "${has_monitor}" || -n "${has_overlay}" || -n "${has_boot}" ]]; then
      warn "Verdict: FAIL? unexpected service start in no-lock scenario"
    elif [[ -z "${has_schedule}" || -n "${has_cancel}" ]]; then
      info "Verdict: PASS (no-lock: heartbeat not scheduled or canceled)"
    else
      warn "Verdict: FAIL? unexpected heartbeat schedule in no-lock scenario"
    fi
  fi
}

evaluate_runtime_logs() {
  local outfile="$1"
  local has_heartbeat has_monitor has_overlay
  has_heartbeat=$(grep -m1 -E "WatchdogWorker: WorkManager heartbeat|WatchdogScheduler: Schedule heartbeat" "${outfile}" || true)
  has_monitor=$(grep -m1 -E "LockMonitorService.*onStartCommand" "${outfile}" || true)
  has_overlay=$(grep -m1 -E "OverlayLockService.*onStartCommand" "${outfile}" || true)
  if [[ -n "${has_heartbeat}" || -n "${has_monitor}" || -n "${has_overlay}" ]]; then
    info "Verdict: PASS (runtime: heartbeat/service logs observed)"
  else
    warn "Verdict: WARN? runtime logs not observed; confirm with dumpsys"
  fi
}

send_test_status() {
  local clear_logs="${1:-1}"
  local action="${ACTION_PREFIX}.action.TEST_STATUS"
  if ! has_test_receiver "${action}"; then
    info "Skip TEST_STATUS (TestControlReceiver not found)"
    return
  fi
  info "Post-boot TEST_STATUS broadcast"
  if [[ "${clear_logs}" == "1" ]]; then
    adb_cmd logcat -c || true
  fi
  adb_cmd shell am broadcast \
    -n "$(receiver_component)" \
    -a "${action}" >/dev/null || \
    warn "TEST_STATUS broadcast failed (is debug build installed?)"
  sleep 1
  adb_cmd logcat -d | grep -E "TestControlReceiver|${APP_ID}" || true
  if [[ "${clear_logs}" == "1" ]]; then
    adb_cmd logcat -c || true
  fi
}

has_test_receiver() {
  local action="$1"
  adb_cmd shell pm query-receivers -a "${action}" 2>/dev/null | grep -q "TestControlReceiver"
}

receiver_component() {
  echo "${APP_ID}/${RECEIVER_CLASS}"
}

device_now_millis() {
  local raw
  raw="$(adb_cmd shell date +%s%3N 2>/dev/null | tr -d '\r' || true)"
  if [[ "$raw" =~ ^[0-9]{13}$ ]]; then
    echo "$raw"
    return
  fi
  local sec
  sec="$(adb_cmd shell date +%s 2>/dev/null | tr -d '\r' || true)"
  if [[ "$sec" =~ ^[0-9]+$ ]]; then
    echo $((sec * 1000))
    return
  fi
  now_millis
}

test_receiver_status_line() {
  local action="${ACTION_PREFIX}.action.TEST_STATUS"
  if ! has_test_receiver "${action}"; then
    return 1
  fi
  adb_cmd logcat -c || true
  adb_cmd shell am broadcast \
    -n "$(receiver_component)" \
    -a "${action}" >/dev/null || return 1
  sleep 1
  adb_cmd logcat -d | grep -E "TestControlReceiver: Status DP snapshot" | tail -n 1 || true
}

test_receiver_lock_active() {
  local line now_ms is_locked end_value
  line="$(test_receiver_status_line)"
  if [[ -z "$line" ]]; then
    return 1
  fi
  is_locked=$(echo "$line" | sed -n 's/.*isLocked=\\(true\\|false\\).*/\\1/p')
  if [[ "$is_locked" != "true" ]]; then
    return 1
  fi
  end_value=$(echo "$line" | sed -n 's/.*end=\\([0-9]\\+\\).*/\\1/p')
  if [[ -n "$end_value" ]]; then
    now_ms="$(device_now_millis)"
    if (( end_value <= now_ms )); then
      return 1
    fi
  fi
  return 0
}

start_lock_via_test_receiver() {
  local end_timestamp="${1:-}"
  local action="${ACTION_PREFIX}.action.TEST_LOCK"
  if ! has_test_receiver "${action}"; then
    return 1
  fi
  ensure_permissions
  launch_main
  sleep 1
  info "Trigger TEST_LOCK via TestControlReceiver (minutes=${LOCK_MINUTES})"
  adb_cmd logcat -c || true
  local cmd=("adb_cmd" "shell" "am" "broadcast" "-n" "$(receiver_component)" "-a" "${action}" "--el" "extra_duration_minutes" "${LOCK_MINUTES}")
  if [[ -n "${end_timestamp}" ]]; then
    cmd+=("--el" "extra_end_timestamp" "${end_timestamp}")
  fi
  "${cmd[@]}" >/dev/null || \
    return 1
  sleep 1
  if adb_cmd logcat -d | grep -Eq "TestControlReceiver: Test lock scheduled"; then
    return 0
  fi
  warn "TestControlReceiver did not log lock scheduling; fallback to UI automation"
  return 1
}

unlock_via_test_receiver() {
  local action="${ACTION_PREFIX}.action.TEST_UNLOCK"
  if ! has_test_receiver "${action}"; then
    return 1
  fi
  launch_main
  sleep 1
  info "Trigger TEST_UNLOCK via TestControlReceiver"
  adb_cmd logcat -c || true
  adb_cmd shell am broadcast \
    -n "$(receiver_component)" \
    -a "${action}" >/dev/null || \
    return 1
  sleep 1
  adb_cmd logcat -d | grep -Eq "TestControlReceiver: Test unlock executed" || true
  return 0
}

reboot_and_wait() {
  info "Rebooting device..."
  adb_cmd reboot
  wait_for_boot
  info "Post-boot settling... (${POST_BOOT_WAIT_SECONDS}s)"
  sleep "${POST_BOOT_WAIT_SECONDS}"
  info "Device back online."
}

ensure_permissions() {
  info "Ensuring runtime/appops permissions (best-effort)"
  adb_cmd shell pm grant "${APP_ID}" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  adb_cmd shell appops set "${APP_ID}" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1 || true
  adb_cmd shell appops set "${APP_ID}" GET_USAGE_STATS allow >/dev/null 2>&1 || true
  adb_cmd shell appops set "${APP_ID}" SCHEDULE_EXACT_ALARM allow >/dev/null 2>&1 || true
}

uiautomator_dump() {
  adb_cmd shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb_cmd shell wm dismiss-keyguard >/dev/null 2>&1 || true
  for _ in {1..3}; do
    adb_cmd shell uiautomator dump /sdcard/uidump.xml >/dev/null 2>&1 || true
    local xml
    xml="$(adb_cmd shell cat /sdcard/uidump.xml 2>/dev/null || true)"
    if [[ "$xml" == "<?xml"* || "$xml" == "<hierarchy"* ]]; then
      echo "$xml"
      return 0
    fi
    sleep 1
  done
  return 1
}

tap_text() {
  local target="$1"
  local xml
  xml="$(uiautomator_dump)" || return 1
  if [[ -z "$xml" ]]; then
    return 1
  fi
  local coords
  coords=$(printf '%s' "$xml" | python3 - "$target" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

target = sys.argv[1]
xml = sys.stdin.read()
if not xml.strip():
    sys.exit(1)
try:
    root = ET.fromstring(xml)
except ET.ParseError:
    sys.exit(1)

def bounds_to_center(bounds: str):
    m = re.findall(r'\\[(\\d+),(\\d+)\\]', bounds or '')
    if len(m) != 2:
        return None
    (x1, y1), (x2, y2) = m
    x1, y1, x2, y2 = int(x1), int(y1), int(x2), int(y2)
    return (x1 + x2) // 2, (y1 + y2) // 2

for node in root.iter():
    text = node.attrib.get('text') or ''
    desc = node.attrib.get('content-desc') or ''
    if text == target or desc == target:
        center = bounds_to_center(node.attrib.get('bounds'))
        if center:
            print(f"{center[0]} {center[1]}")
            sys.exit(0)
sys.exit(1)
PY
  ) || return 1
  adb_cmd shell input tap $coords
}

get_screen_size() {
  local size
  size=$(adb_cmd shell wm size 2>/dev/null | awk -F': ' '/Physical/{print $2; exit}')
  if [[ -z "$size" ]]; then
    echo "1080 2400"
    return
  fi
  local width="${size%x*}"
  local height="${size#*x}"
  echo "${width} ${height}"
}

tap_fallback_lock_button() {
  local width height
  read -r width height <<<"$(get_screen_size)"
  local x=$((width / 2))
  local y=$((height * 92 / 100))
  adb_cmd shell input tap "$x" "$y"
}

tap_fallback_confirm_button() {
  local width height
  read -r width height <<<"$(get_screen_size)"
  local x=$((width * 73 / 100))
  local y=$((height * 56 / 100))
  adb_cmd shell input tap "$x" "$y"
}

launch_main() {
  adb_cmd shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb_cmd shell wm dismiss-keyguard >/dev/null 2>&1 || true
  adb_cmd shell monkey -p "${APP_ID}" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
  adb_cmd shell am start -n "${APP_ID}/jp.kawai.ultrafocus.MainActivity" >/dev/null 2>&1 || true
}

start_lock_auto() {
  ensure_permissions
  launch_main
  sleep 2
  local attempt
  for attempt in 1 2 3; do
    if tap_text "ロック開始"; then
      sleep 1
      if tap_text "開始する"; then
        info "Lock start triggered via UI automation."
        return 0
      fi
      tap_fallback_confirm_button
      sleep 1
      if tap_text "開始する"; then
        info "Lock start triggered via UI automation (fallback tap)."
        return 0
      fi
    fi
    tap_fallback_lock_button
    sleep 1
    if tap_text "開始する"; then
      info "Lock start triggered via UI automation (fallback lock tap)."
      return 0
    fi
    warn "UI automation retry ${attempt} failed; retrying..."
    sleep 2
  done
  warn "Failed to tap lock buttons. Falling back to manual prompt."
  return 1
}

prompt_lock_if_needed() {
  local label="$1"
  if dp_lock_active; then
    info "Lock already active in DP store; skip start"
    return
  fi
  if start_lock_via_test_receiver; then
    return
  fi
  if [[ "$AUTO_LOCK" == "1" ]]; then
    if ! start_lock_auto; then
      info "Prepare scenario: ${label}"
      info "ロックを開始して Enter を押してください。"
      read -r _
    fi
    return
  fi
  info "Prepare scenario: ${label}"
  info "ロックを開始して Enter を押してください。"
  read -r _
}

scenario_no_lock() {
  info "Scenario: no_lock"
  unlock_via_test_receiver || true
  clear_dp_lock_state
  local tmp
  tmp=$(mktemp)
  collect_logs "${tmp}"
  reboot_and_wait
  info "Post-boot lock state (DP):"
  read_lock_state
  sleep "${POST_BOOT_SERVICE_WAIT_SECONDS}"
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 0
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
  sleep "${POST_BOOT_SERVICE_WAIT_SECONDS}"
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 1
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
  sleep "${POST_BOOT_SERVICE_WAIT_SECONDS}"
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 1
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
  sleep "${POST_BOOT_SERVICE_WAIT_SECONDS}"
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 1
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
  sleep "${POST_BOOT_SERVICE_WAIT_SECONDS}"
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 1
}

scenario_lock_60m() {
  info "Scenario: lock_60m"
  local tmp prev_minutes
  tmp=$(mktemp)
  collect_logs "${tmp}"
  prev_minutes="${LOCK_MINUTES}"
  LOCK_MINUTES="${LOCK_MINUTES_LONG}"
  prompt_lock_if_needed "start lock now (reboot after ~3600s)"
  LOCK_MINUTES="${prev_minutes}"
  sleep "${LOCK_60M_WAIT_SECONDS}"
  reboot_and_wait
  info "Post-boot lock state (DP):"
  read_lock_state
  sleep "${POST_BOOT_SERVICE_WAIT_SECONDS}"
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 1
}

scenario_lock_60m_no_reboot() {
  info "Scenario: lock_60m_no_reboot"
  local tmp prev_minutes
  tmp=$(mktemp)
  collect_logs "${tmp}"
  prev_minutes="${LOCK_MINUTES}"
  LOCK_MINUTES="${LOCK_MINUTES_60M_NO_REBOOT}"
  prompt_lock_if_needed "start lock now (no reboot after ~3600s)"
  LOCK_MINUTES="${prev_minutes}"
  if [[ "${FORCE_DEVICEIDLE}" == "1" ]]; then
    info "Force device idle (${DEVICEIDLE_MODE})"
    deviceidle_force "${DEVICEIDLE_MODE}"
    info "Device idle status: $(deviceidle_status)"
  fi
  sleep "${LOCK_60M_WAIT_SECONDS}"
  if [[ "${FORCE_DEVICEIDLE}" == "1" ]]; then
    deviceidle_unforce
  fi
  info "Runtime lock state (DP):"
  read_lock_state
  info "Runtime service state:"
  dump_service_state
  info "Runtime key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_runtime_logs "${tmp}"
}

scenario_lock_90m() {
  info "Scenario: lock_90m"
  local tmp prev_minutes orig_auto_time orig_auto_tz
  tmp=$(mktemp)
  collect_logs "${tmp}"
  prev_minutes="${LOCK_MINUTES}"
  LOCK_MINUTES="${LOCK_MINUTES_90M}"
  prompt_lock_if_needed "start lock now (reboot after ~5400s)"
  LOCK_MINUTES="${prev_minutes}"
  if [[ "${FAST_FORWARD_TIME_SHIFT}" == "1" ]]; then
    local orig_time
    orig_time="$(device_now_millis)"
    orig_auto_time="$(adb_cmd shell settings get global auto_time 2>/dev/null | tr -d '\r')"
    orig_auto_tz="$(adb_cmd shell settings get global auto_time_zone 2>/dev/null | tr -d '\r')"
    set_auto_time 0
    info "Fast-forward time by ${LOCK_90M_WAIT_SECONDS}s"
    set_device_time_millis "$((orig_time + LOCK_90M_WAIT_SECONDS * 1000))" || warn "Fast-forward failed; falling back to sleep"
  else
    sleep "${LOCK_90M_WAIT_SECONDS}"
  fi
  reboot_and_wait
  info "Post-boot lock state (DP):"
  read_lock_state
  sleep "${POST_BOOT_SERVICE_WAIT_SECONDS}"
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 1
  if [[ "${FAST_FORWARD_TIME_SHIFT}" == "1" ]]; then
    restore_setting auto_time "${orig_auto_time}"
    restore_setting auto_time_zone "${orig_auto_tz}"
  fi
}

scenario_lock_120m() {
  info "Scenario: lock_120m"
  local tmp prev_minutes orig_auto_time orig_auto_tz
  tmp=$(mktemp)
  collect_logs "${tmp}"
  prev_minutes="${LOCK_MINUTES}"
  LOCK_MINUTES="${LOCK_MINUTES_120M}"
  prompt_lock_if_needed "start lock now (reboot after ~7200s)"
  LOCK_MINUTES="${prev_minutes}"
  if [[ "${FAST_FORWARD_TIME_SHIFT}" == "1" ]]; then
    local orig_time
    orig_time="$(device_now_millis)"
    orig_auto_time="$(adb_cmd shell settings get global auto_time 2>/dev/null | tr -d '\r')"
    orig_auto_tz="$(adb_cmd shell settings get global auto_time_zone 2>/dev/null | tr -d '\r')"
    set_auto_time 0
    info "Fast-forward time by ${LOCK_120M_WAIT_SECONDS}s"
    set_device_time_millis "$((orig_time + LOCK_120M_WAIT_SECONDS * 1000))" || warn "Fast-forward failed; falling back to sleep"
  else
    sleep "${LOCK_120M_WAIT_SECONDS}"
  fi
  reboot_and_wait
  info "Post-boot lock state (DP):"
  read_lock_state
  sleep "${POST_BOOT_SERVICE_WAIT_SECONDS}"
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 1
  if [[ "${FAST_FORWARD_TIME_SHIFT}" == "1" ]]; then
    restore_setting auto_time "${orig_auto_time}"
    restore_setting auto_time_zone "${orig_auto_tz}"
  fi
}

scenario_screen_off_long() {
  info "Scenario: screen_off_long"
  local tmp prev_minutes
  tmp=$(mktemp)
  collect_logs "${tmp}"
  prev_minutes="${LOCK_MINUTES}"
  LOCK_MINUTES="${LOCK_MINUTES_SCREEN_OFF}"
  prompt_lock_if_needed "start lock now (screen off long)"
  LOCK_MINUTES="${prev_minutes}"
  info "Turning screen off for ${SCREEN_OFF_WAIT_SECONDS}s"
  screen_off
  if [[ "${FORCE_DEVICEIDLE}" == "1" ]]; then
    info "Force device idle (${DEVICEIDLE_MODE})"
    deviceidle_force "${DEVICEIDLE_MODE}"
    info "Device idle status: $(deviceidle_status)"
  fi
  sleep "${SCREEN_OFF_WAIT_SECONDS}"
  if [[ "${FORCE_DEVICEIDLE}" == "1" ]]; then
    deviceidle_unforce
  fi
  screen_on
  info "Runtime lock state (DP):"
  read_lock_state
  info "Runtime service state:"
  dump_service_state
  info "Runtime key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_runtime_logs "${tmp}"
}

scenario_battery_saver_on() {
  info "Scenario: battery_saver_on"
  local tmp prev_minutes
  tmp=$(mktemp)
  collect_logs "${tmp}"
  prev_minutes="${LOCK_MINUTES}"
  LOCK_MINUTES="${LOCK_MINUTES_BATTERY_SAVER}"
  prompt_lock_if_needed "start lock now (battery saver on)"
  LOCK_MINUTES="${prev_minutes}"
  info "Enable battery saver"
  set_battery_saver 1
  info "Battery saver status: $(battery_saver_status)"
  if [[ "${FORCE_DEVICEIDLE}" == "1" ]]; then
    info "Force device idle (${DEVICEIDLE_MODE})"
    deviceidle_force "${DEVICEIDLE_MODE}"
    info "Device idle status: $(deviceidle_status)"
  fi
  sleep "${BATTERY_SAVER_WAIT_SECONDS}"
  if [[ "${FORCE_DEVICEIDLE}" == "1" ]]; then
    deviceidle_unforce
  fi
  reboot_and_wait
  info "Post-boot lock state (DP):"
  read_lock_state
  sleep "${POST_BOOT_SERVICE_WAIT_SECONDS}"
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 1
  set_battery_saver 0
}

scenario_time_shift() {
  info "Scenario: time_shift"
  local tmp prev_minutes
  tmp=$(mktemp)
  collect_logs "${tmp}"
  prev_minutes="${LOCK_MINUTES}"
  LOCK_MINUTES="${LOCK_MINUTES_60M_NO_REBOOT}"
  prompt_lock_if_needed "start lock now (time shift)"
  LOCK_MINUTES="${prev_minutes}"
  local orig_time orig_auto_time orig_auto_tz
  orig_time="$(device_now_millis)"
  orig_auto_time="$(adb_cmd shell settings get global auto_time 2>/dev/null | tr -d '\r')"
  orig_auto_tz="$(adb_cmd shell settings get global auto_time_zone 2>/dev/null | tr -d '\r')"
  set_auto_time 0
  local forward_ms back_ms
  forward_ms=$((orig_time + TIME_SHIFT_FORWARD_SECONDS * 1000))
  back_ms=$((orig_time - TIME_SHIFT_BACK_SECONDS * 1000))
  if ! set_device_time_millis "${forward_ms}"; then
    warn "Failed to shift time forward; skipping time_shift scenario"
    restore_setting auto_time "${orig_auto_time}"
    restore_setting auto_time_zone "${orig_auto_tz}"
    return
  fi
  sleep 2
  info "After time shift forward:"
  read_lock_state
  if ! set_device_time_millis "${back_ms}"; then
    warn "Failed to shift time backward"
  fi
  sleep 2
  info "After time shift backward:"
  read_lock_state
  set_device_time_millis "${orig_time}" || true
  restore_setting auto_time "${orig_auto_time}"
  restore_setting auto_time_zone "${orig_auto_tz}"
  info "Runtime key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_runtime_logs "${tmp}"
}

scenario_dp_corrupt_missing_end() {
  info "Scenario: dp_corrupt_missing_end"
  local tmp
  tmp=$(mktemp)
  collect_logs "${tmp}"
  prompt_lock_if_needed "start lock now (corrupt DP missing end)"
  if ! corrupt_dp_remove_lock_end; then
    warn "Failed to corrupt DP lock_end_timestamp; skipping"
    return
  fi
  info "Corrupted DP: lock_end_timestamp removed"
  reboot_and_wait
  info "Post-boot lock state (DP):"
  read_lock_state
  sleep "${POST_BOOT_SERVICE_WAIT_SECONDS}"
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  # Observation-only: lock_end missing may still trigger boot receivers but overlay stops.
  evaluate_logs "${tmp}" 1
}

scenario_cold_boot() {
  info "Scenario: cold_boot"
  local tmp avd_name prev_serial
  tmp=$(mktemp)
  collect_logs "${tmp}"
  prompt_lock_if_needed "start lock now (emulator cold boot)"
  avd_name="${AVD_NAME:-}"
  if [[ -z "${avd_name}" ]]; then
    avd_name="$(current_avd_name || true)"
  fi
  prev_serial="${SERIAL}"
  info "Stopping emulator (${prev_serial})..."
  adb_cmd emu kill || true
  sleep 2
  if ! start_emulator_cold_boot "${avd_name}"; then
    warn "Failed to start emulator cold boot. Skipping."
    return
  fi
  info "Waiting for emulator to reconnect..."
  adb wait-for-device >/dev/null
  SERIAL=""
  pick_serial
  wait_for_boot
  info "Post-boot settling... (${POST_BOOT_WAIT_SECONDS}s)"
  sleep "${POST_BOOT_WAIT_SECONDS}"
  info "Post-boot lock state (DP):"
  read_lock_state
  sleep "${POST_BOOT_SERVICE_WAIT_SECONDS}"
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 1
}

scenario_power_off_boot() {
  info "Scenario: power_off_boot"
  local tmp
  tmp=$(mktemp)
  collect_logs "${tmp}"
  prompt_lock_if_needed "start lock now (power off -> boot)"
  info "端末を電源OFFにしてください。完了したら Enter を押してください。"
  read -r _
  info "端末を電源ONにして起動してください（復帰後は自動で進行します）。"
  adb wait-for-device >/dev/null
  wait_for_boot
  info "Post-boot settling... (${POST_BOOT_WAIT_SECONDS}s)"
  sleep "${POST_BOOT_WAIT_SECONDS}"
  info "Post-boot lock state (DP):"
  read_lock_state
  sleep "${POST_BOOT_SERVICE_WAIT_SECONDS}"
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 1
}

scenario_lock_end_before() {
  info "Scenario: lock_end_before"
  local tmp now_ms end_ms remaining wait_seconds
  tmp=$(mktemp)
  collect_logs "${tmp}"
  now_ms="$(device_now_millis)"
  end_ms=$((now_ms + LOCK_END_BEFORE_OFFSET_SECONDS * 1000))
  if ! start_lock_via_test_receiver "${end_ms}"; then
    warn "Falling back to UI automation for lock_end_before"
    prompt_lock_if_needed "start lock now (lock_end_before)"
  fi
  remaining=$(((end_ms - now_ms) / 1000))
  if (( remaining > LOCK_END_BEFORE_TARGET_SECONDS )); then
    wait_seconds=$((remaining - LOCK_END_BEFORE_TARGET_SECONDS))
    info "Waiting ${wait_seconds}s to reach boundary (remaining ~${LOCK_END_BEFORE_TARGET_SECONDS}s)"
    sleep "${wait_seconds}"
  else
    warn "Remaining time already below target; proceeding to reboot"
  fi
  reboot_and_wait
  info "Post-boot lock state (DP):"
  read_lock_state
  sleep "${POST_BOOT_SERVICE_WAIT_SECONDS}"
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 1
}

scenario_lock_end_after() {
  info "Scenario: lock_end_after"
  local tmp now_ms end_ms
  tmp=$(mktemp)
  collect_logs "${tmp}"
  now_ms="$(device_now_millis)"
  end_ms=$((now_ms + LOCK_END_AFTER_OFFSET_SECONDS * 1000))
  if ! start_lock_via_test_receiver "${end_ms}"; then
    warn "Falling back to UI automation for lock_end_after"
    prompt_lock_if_needed "start lock now (lock_end_after)"
  fi
  reboot_and_wait
  info "Post-boot lock state (DP):"
  read_lock_state
  sleep "${POST_BOOT_SERVICE_WAIT_SECONDS}"
  info "Post-boot key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 0
}

scenario_my_package_replaced() {
  info "Scenario: my_package_replaced"
  local tmp prev_minutes
  tmp=$(mktemp)
  collect_logs "${tmp}"
  prev_minutes="${LOCK_MINUTES}"
  LOCK_MINUTES="${LOCK_MINUTES_LONG}"
  prompt_lock_if_needed "start lock now (reinstall debug apk)"
  LOCK_MINUTES="${prev_minutes}"
  info "Reinstalling debug build to trigger MY_PACKAGE_REPLACED..."
  if [[ -n "${APK_PATH:-}" && -f "${APK_PATH}" ]]; then
    adb_cmd install -r "${APK_PATH}" >/dev/null
  else
    ./gradlew installDebug >/dev/null
  fi
  sleep 3
  info "Post-install lock state (DP):"
  read_lock_state
  sleep "${POST_BOOT_SERVICE_WAIT_SECONDS}"
  info "Post-install key logs:"
  tmp=$(mktemp)
  collect_logs "${tmp}"
  evaluate_logs "${tmp}" 1
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
    lock_60m) scenario_lock_60m ;;
    lock_60m_no_reboot) scenario_lock_60m_no_reboot ;;
    lock_90m) scenario_lock_90m ;;
    lock_120m) scenario_lock_120m ;;
    screen_off_long) scenario_screen_off_long ;;
    battery_saver_on) scenario_battery_saver_on ;;
    time_shift) scenario_time_shift ;;
    dp_corrupt_missing_end) scenario_dp_corrupt_missing_end ;;
    cold_boot) scenario_cold_boot ;;
    power_off_boot) scenario_power_off_boot ;;
    lock_end_before) scenario_lock_end_before ;;
    lock_end_after) scenario_lock_end_after ;;
    my_package_replaced) scenario_my_package_replaced ;;
    all)
      for s in "${SCENARIOS[@]}"; do
        run "$s"
      done
      ;;
    *)
      echo "Usage: $0 {all|no_lock|lock_immediate|lock_30s|lock_3m|lock_10m|lock_60m|lock_60m_no_reboot|lock_90m|lock_120m|screen_off_long|battery_saver_on|time_shift|dp_corrupt_missing_end|cold_boot|power_off_boot|lock_end_before|lock_end_after|my_package_replaced}" >&2
      exit 1
      ;;
  esac
}

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 {all|no_lock|lock_immediate|lock_30s|lock_3m|lock_10m|lock_60m|lock_60m_no_reboot|lock_90m|lock_120m|screen_off_long|battery_saver_on|time_shift|dp_corrupt_missing_end|cold_boot|power_off_boot|lock_end_before|lock_end_after|my_package_replaced}" >&2
  exit 1
fi

run "$1"
