# 再起動時オーバーレイ復帰 調査報告（2026-01-16）

最終確認日: 2026-01-18
更新理由: 2026/01/18 dp_corrupt_missing_end / force-idle 併用 / cold_boot の追加検証結果、実機記録テンプレート、実機向け追加シナリオ（起動後のユーザー解錠遷移/解錠タイミング差分/バッテリー最適化差分）を反映。

## 調査目的
- 端末再起動後にオーバーレイが復帰する実装になっているかを確認し、復帰失敗の懸念点を洗い出す。

## 調査範囲と方法
- 対象コード: Boot レシーバ、Direct Boot ストア、Overlay/Monitor サービス、Watchdog、WorkManager。
- 参照ドキュメント: `/docs/roadmap/implementation_plan.md`, `/docs/research/overlay_reliability_investigation_plan.md`。
- 実機検証: エミュレータで実施（Medium_Phone_API_36.1_3 / Android 16）。
- 補足: API 36 では TestControlReceiver のコンポーネント名が `jp.kawai.phonefasting/jp.kawai.ultrafocus.receiver.TestControlReceiver` であり、`.receiver.TestControlReceiver` 指定だと受信されない。スクリプトを修正して TestControlReceiver 経由の DP 状態取得に切り替えた。
- 補足: `run-as` で `/data/user_de` を直接読み書きできないため、DP スナップショットは TestControlReceiver のログ出力で確認。

## 実装確認サマリ（再起動復帰の経路）
- **BootFastStartupReceiver** が Direct Boot の SharedPreferences を読み、ロック有効なら即 `OverlayLockService` / `LockMonitorService` を起動。5s/30s/90s の再試行あり。
  - 対応コード: `app/src/main/java/jp/kawai/ultrafocus/receiver/BootFastStartupReceiver.kt`
- **BootCompletedReceiver** が CE/DP スナップショットを突き合わせ、ロック有効ならサービス再起動＋Watchdog 再設定。4 秒後の再試行あり。
  - 対応コード: `app/src/main/java/jp/kawai/ultrafocus/receiver/BootCompletedReceiver.kt`
- **directBootAware** 指定により、未解錠でも受信・起動可能。
  - 対応コード: `app/src/main/AndroidManifest.xml`
- **DirectBootLockStateStore** にロック状態を保存し、再起動後の判定に使用。
  - 対応コード: `app/src/main/java/jp/kawai/ultrafocus/data/datastore/DirectBootLockStateStore.kt`, `DataStoreManager.kt`
- **OverlayLockService** は未解錠時 DP ストアを参照し、解錠後 CE へ切替。
  - 対応コード: `app/src/main/java/jp/kawai/ultrafocus/service/OverlayLockService.kt`
- **Watchdog（Alarm/WorkManager）** による継続復旧。
  - 対応コード: `app/src/main/java/jp/kawai/ultrafocus/receiver/WatchdogReceiver.kt`,
    `service/WatchdogScheduler.kt`, `service/WatchdogWorker.kt`, `service/WatchdogWorkScheduler.kt`

結論: **再起動時にオーバーレイ復帰を試みる実装は確認できる**。  
エミュレータ上では lock_immediate / lock_30s / lock_3m / lock_10m / lock_60m（実時間60分待機・ロック設定1時間5分）に加え、battery_saver_on / screen_off_long / lock_60m_no_reboot / lock_90m / lock_120m / time_shift / dp_corrupt_missing_end / cold_boot の追加検証ログを確認できた（短縮・時間シフト・force-idle 併用あり）。

---

## 実機シナリオテスト結果（エミュレータ）
実施日: 2026-01-16  
端末: Medium_Phone_API_36.1_3（Android 16 / API 36）  
前提: 必須4権限はすべて ON（権限剥奪後の再起動復帰は仕様対象外）。ロック状態を Direct Boot ストアに保存した上で「待機→再起動」で評価。  
備考: エミュレータのログ時刻は 2026-01-17 表記（端末時計が先行）だが、検証は 2026-01-16 に実施。

- lock_immediate: PASS（BootCompletedReceiver / BootFastStartupReceiver からサービス再起動）
- lock_30s: PASS
- lock_3m: PASS
- lock_10m: PASS
- 参考: lock_immediate + 通知権限 OFF: PASS（仕様外。foreground を抑止して継続し、再起動後の復帰ログを確認）

## 追加シナリオテスト結果（エミュレータ）
実施日: 2026-01-17  
端末: Medium_Phone_API_36.1_3（Android 16 / API 36）  
前提: 必須4権限はすべて ON。TestControlReceiver で DP ロック状態を設定・確認。  
備考: lock_end_before はブート時間を考慮し「残り約120秒で再起動」設定（`LOCK_END_BEFORE_OFFSET_SECONDS=300`, `LOCK_END_BEFORE_TARGET_SECONDS=120`）。

- no_lock: PASS（BootCompletedReceiver が “No active lock state” を記録、サービス起動なし）
- lock_end_before: PASS（LOCKED_BOOT_COMPLETED でサービス復帰ログを確認）
- lock_end_after: PASS（終了直後再起動で復帰せず）
- my_package_replaced: PASS（MY_PACKAGE_REPLACED でサービス再起動）
- lock_60m: PASS（`LOCK_60M_WAIT_SECONDS=3600` で実時間60分待機後に再起動。ロック設定は1時間5分。待機中のオーバーレイ継続はログ監視なし）

## 追加シナリオテスト結果（エミュレータ・2026-01-18）
実施日: 2026-01-18  
端末: Medium_Phone_API_36.1_3（Android 16 / API 36）  
前提: 必須4権限はすべて ON。TestControlReceiver でロック付与/解除。  
備考: 以下は **短縮版** / **時間シフト（FAST_FORWARD_TIME_SHIFT=1）** / **force-idle（FORCE_DEVICEIDLE=1）** を用いた検証。時間シフトによりログ時刻が 2026-01-19 表記になることがあるが、実施日は 2026-01-18。長時間の実時間検証は別途実機で実施推奨。

- time_shift: PASS（`cmd alarm set-time` で時刻を前後、ロック継続を確認）
- battery_saver_on: PASS（`BATTERY_SAVER_WAIT_SECONDS=300` + force-idle 併用で再起動）
- screen_off_long: PASS（`SCREEN_OFF_WAIT_SECONDS=300` + force-idle 併用、再起動なし）
- lock_60m_no_reboot: PASS（`LOCK_60M_WAIT_SECONDS=600` + force-idle 併用、再起動なし）
- lock_90m: PASS（時間シフトで 90 分相当へジャンプ後に再起動）
- lock_120m: PASS（時間シフトで 120 分相当へジャンプ後に再起動）
- dp_corrupt_missing_end: PASS（lock_end_timestamp を削除して再起動 → Boot レシーバ/サービス起動ログを確認。`lockEnd=null` を観測）
- cold_boot: PASS（エミュレータを power off → cold boot して復帰ログを確認）

実行メモ:
- `FORCE_DEVICEIDLE=1 BATTERY_SAVER_WAIT_SECONDS=300 bash tools/reboot_scenarios.sh battery_saver_on`
- `FORCE_DEVICEIDLE=1 SCREEN_OFF_WAIT_SECONDS=300 bash tools/reboot_scenarios.sh screen_off_long`
- `FORCE_DEVICEIDLE=1 LOCK_60M_WAIT_SECONDS=600 bash tools/reboot_scenarios.sh lock_60m_no_reboot`
- `FAST_FORWARD_TIME_SHIFT=1 bash tools/reboot_scenarios.sh lock_90m`
- `FAST_FORWARD_TIME_SHIFT=1 bash tools/reboot_scenarios.sh lock_120m`
- `bash tools/reboot_scenarios.sh time_shift`
- `bash tools/reboot_scenarios.sh dp_corrupt_missing_end`
- `bash tools/reboot_scenarios.sh cold_boot`

## Doze 強制テスト（エミュレータ）
実施日: 2026-01-16  
端末: Medium_Phone_API_36.1_3（Android 16 / API 36）  
手順: ロック開始 → `cmd deviceidle force-idle` → 30 分放置 → ログ確認。  
結果: PASS（30 分経過後も WatchdogWorker / OverlayLockService / LockMonitorService の定期ログを確認。`mState=IDLE` を維持）

## Exact Alarm 拒否テスト（エミュレータ）
実施日: 2026-01-16  
端末: Medium_Phone_API_36.1_3（Android 16 / API 36）  
手順: `appops set SCHEDULE_EXACT_ALARM deny` → lock_immediate 再起動シナリオ。  
結果: PASS（`Exact alarm not allowed` / `Exact alarm retry failed; fallback to inexact` を確認しつつ復帰ログあり）

---

## 批判的レビュー方針（確度ラベル）
- **確度: 高** = コード上で確実に言える。
- **確度: 中** = OS 仕様・端末差・権限制限に依存し、実機検証が必要。
- **確度: 低** = 例外的な破損/レース条件を前提とする。

---

## 懸念点（確度付き）

### P2（確度: 低）通知権限 OFF 時の安定性（仕様外・参考）
- **確認内容**: `POST_NOTIFICATIONS` なしでも `startService` で起動し、foreground を抑止する。再起動復帰はエミュレータで PASS。
- **観測**: 通知権限 OFF 状態では `ActivityManager` 側でサービス再起動スケジュールが発生（再起動後は復帰ログを確認）。
- **根拠コード**:
  - `app/src/main/java/jp/kawai/ultrafocus/service/OverlayLockService.kt`
  - `app/src/main/java/jp/kawai/ultrafocus/service/LockMonitorService.kt`
- **備考**: 4権限必須の設計上は仕様対象外。耐性確認として記録する。

### P2（確度: 低）Foreground 降格 + WakeLock タイムアウト + Doze
- **確認内容**: 両サービスは起動後に `demoteForegroundIfNeeded()` を呼び Foreground を降格。WakeLock は 180 秒。
- **観測**: 10 分シナリオは PASS（ただし Doze 強制は未実施）。
- **根拠コード**:
  - `app/src/main/java/jp/kawai/ultrafocus/service/OverlayLockService.kt`
  - `app/src/main/java/jp/kawai/ultrafocus/service/LockMonitorService.kt`
- **関連ドキュメント**: `/docs/research/overlay_reliability_investigation_plan.md`

### P2（確度: 低）DP 状態の欠損・部分欠損による false negative
- **確認内容**: Direct Boot ストア書き込み失敗はログのみ。`OverlayLockService` の描画条件は `lockEndTimestamp != null` が必須。
- **想定リスク**: DP に `is_locked=true` が残っていても `lockEndTimestamp` が欠けると即停止。
- **追加検証**: `dp_corrupt_missing_end` で `lock_end_timestamp` を削除した場合、Boot レシーバは起動ログを出す一方で `WatchdogWorker` に `lockEnd=null` が出力されることを確認。
- **根拠コード**:
  - `app/src/main/java/jp/kawai/ultrafocus/data/datastore/DirectBootLockStateStore.kt`
  - `app/src/main/java/jp/kawai/ultrafocus/service/OverlayLockService.kt`
- **備考**: 通常フローでは `lockEndTimestamp` は必ず書き込まれるため、**破損/旧バージョン状態**を前提とした低確度。

### P2（確度: 低）システム時刻変更による判定ズレ
- **確認内容**: ロック有効判定に `System.currentTimeMillis()` を利用。NTP 補正や手動変更で終了時刻が前後する可能性。
- **想定リスク**: 早期解除または過剰継続。

### P2（確度: 低）Boot レシーバの exported とリトライ Action
- **確認内容**: `BootFastStartupReceiver` が `exported=true` で `ACTION_RETRY` を受け取れる。
- **想定リスク**: 第三者アプリから不要起動される可能性（DoS/乱発）。復帰信頼性への直接影響は小さいが安定性面の懸念。

### P3（確度: 低）緊急解除アクティブ状態による一時的な抑止（仕様上の挙動）
- **確認内容**: `EmergencyUnlockStateStore` がアクティブだと `OverlayLockService.start()` は起動をスキップする。
- **再評価**: `EmergencyUnlockStateStore` は `SystemClock.elapsedRealtime()` を保存し、**再起動後は経過時間が巻き戻るため `age < 0` 判定で自動無効化**される。さらに TTL は最大 10 分。
- **結論**: 再起動後まで抑止が継続する可能性は低い（仕様上の一時抑止に限定）。
- **根拠コード**:
  - `app/src/main/java/jp/kawai/ultrafocus/emergency/EmergencyUnlockStateStore.kt`
  - `app/src/main/java/jp/kawai/ultrafocus/service/OverlayLockService.kt`

---

## 既存調査計画との整合・差分
- `/docs/research/overlay_reliability_investigation_plan.md` の「Direct Boot 中の WorkManager 例外」について、現行コードは `WatchdogWorkScheduler` と `WatchdogWorker` が `UserManager.isUserUnlocked` を判定し、未解錠時は WorkManager をキャンセルする実装になっている。
  - 対応コード: `app/src/main/java/jp/kawai/ultrafocus/service/WatchdogWorkScheduler.kt`, `WatchdogWorker.kt`
- ただし **既存ジョブがブート直後に動くレース**や **cancel 実行時の例外**は実機での再検証が必要。

---

## 批判的レビューでの再評価ポイント
- **WorkManager 未解錠問題**: 主要経路はガード済みで、`cancel()` 例外もガード済み。
- **lockEndTimestamp null**: 通常フローでは発生しないため低確度。
- **FGS 起動制限**: 通知権限 OFF の再起動でも復帰を確認。端末差は残るため P2 として継続監視。
- **再起動再試行の多重予約**: `ServiceRestartScheduler` のデバウンスで緩和。

## 修正対応（本調査に基づく変更）
- Boot 再試行で Exact Alarm が使えない場合に inexact へフォールバック。
- WorkManager の cancel で例外が出ても Boot 復帰を止めないようガード。
- サービス起動失敗時に `ServiceRestartScheduler` で再起動を試行するよう補強。
- `ServiceRestartScheduler` の再試行スケジュールに最小間隔のデバウンスを追加。
- `startForeground` の RuntimeException を捕捉し、クラッシュせず foreground 抑止（30s リトライ間隔）で継続。
- 調査スクリプトのログ判定/待機処理を改善（post-boot wait / 判定正規表現 / 画面自動化フォールバック / 端末起動時の停止状態ケア）。
- TestControlReceiver 実行後にサービス起動ログを待機してから評価するよう順序を調整（`POST_BOOT_SERVICE_WAIT_SECONDS`）。
- デバッグ用 TestControlReceiver のロック更新を同期化（テスト時の確実な保存）。
- `startForegroundService` で起動した場合に `startForeground` を強制し、抑止時は停止するよう Overlay/Monitor サービスを補強（`EXTRA_REQUIRE_FOREGROUND`）。

---

## 検証チェックリスト（次回実機調査用）
- [x] エミュレータで lock_immediate / lock_30s / lock_3m / lock_10m を再実施（既存ロック継続）。
- [x] 参考: Android 13+ で通知権限 OFF 状態の再起動復帰（lock_immediate）。
- [x] Doze 強制（30 分）でオーバーレイ維持（ログ上で心拍とサービス再起動を確認）。
- [x] Exact Alarm を拒否した端末で Boot 再試行の挙動（inexact フォールバック確認）。
- [x] no_lock（ロックなし再起動でサービス未起動）。
- [x] lock_end_before（終了前再起動で復帰）。
- [x] lock_end_after（終了後再起動で復帰しない）。
- [x] MY_PACKAGE_REPLACED（アプリ更新相当で復帰）。
- [x] lock_60m（実時間60分待機・ロック設定1時間5分で実施）。
- [x] DP ストア不整合（手動削除/書き込み失敗想定）時の復帰挙動（dp_corrupt_missing_end で確認）。
- [ ] OEM 端末（Samsung / Xiaomi など）での復帰率比較。
- [x] lock_60m_no_reboot（短縮: `LOCK_60M_WAIT_SECONDS=600`）。
- [x] lock_90m / lock_120m（時間シフトで短縮実施）。
- [x] screen_off_long（短縮: `SCREEN_OFF_WAIT_SECONDS=300`）。
- [x] battery_saver_on（短縮: `BATTERY_SAVER_WAIT_SECONDS=300`）。
- [x] time_shift（`cmd alarm set-time` で前後シフト確認）。
- [x] cold_boot（エミュレータ power off → cold boot）。

---

## 実機検証（未実施・要確認）
**注意**: 以下は実機でのみ確認可能。結果は `/docs/research/overlay_reboot_recovery_real_device_log.md` に記録する。

- [ ] power_off_boot（実機の電源OFF→起動）
- [ ] lock_60m（実時間60分→再起動）
- [ ] lock_90m（実時間90分→再起動）
- [ ] lock_120m（実時間120分→再起動）
- [ ] battery_saver_long（省電力ON + 長時間→再起動）
- [ ] screen_off_long（画面OFF + 長時間→ロック維持）
- [ ] OEM差分（Pixel / Samsung / Xiaomi など複数端末で同一手順）
- [ ] unlock_transition（起動→解錠でのDP→CE切替後もオーバーレイ維持）
- [ ] unlock_transition_early（起動直後に解錠）
- [ ] unlock_transition_late（起動後しばらく待って解錠）
- [ ] battery_opt_restricted / battery_opt_optimized / battery_opt_unrestricted（バッテリー最適化差分）

---

## 追加テストケース（提案）
※ 短縮・時間シフトでの確認は実施済み。実時間・実機での再検証は引き続き推奨。
### 高優先
- lock_60m_no_reboot: force-idle 併用の短縮検証は実施済み。実時間 60 分は OEM 実機での再確認が望ましい。
- lock_90m / lock_120m: 時間シフトで検証済み。実時間の追加検証は任意。

### 中優先
- screen_off_long: force-idle 併用の短縮検証は実施済み。実時間の追加検証は任意。
- battery_saver_on: force-idle 併用の短縮検証は実施済み。実時間の追加検証は任意。

### 低優先（端末差）
- oem_devices: Pixel + Samsung 等で再起動復帰率を比較。
- time_shift: システム時刻変更（進める/戻す）で lockEnd 判定のズレを確認。

---

## 参考
- 調査スクリプト: `tools/reboot_scenarios.sh`
- 実装計画: `/docs/roadmap/implementation_plan.md`
- 既存調査計画: `/docs/research/overlay_reliability_investigation_plan.md`
