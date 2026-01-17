# 再起動時オーバーレイ復帰 調査報告（2026-01-16）

最終確認日: 2026-01-17
更新理由: 境界・更新系の再起動シナリオ（no_lock / lock_end_before / lock_end_after / MY_PACKAGE_REPLACED / lock_60m短縮）とテストスクリプトの安定化（TestControlReceiver 直指定・DP 読み取り改善）、FGS 起動例外対策を反映。

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
エミュレータ上では lock_immediate / lock_30s / lock_3m / lock_10m で復帰ログを確認できた。

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
- lock_60m: PASS（`LOCK_60M_WAIT_SECONDS=600` で10分待機後に再起動。実時間60分は手動検証を推奨）

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

### P2（確度: 中）緊急解除アクティブ状態が復帰時のオーバーレイを抑止
- **確認内容**: `EmergencyUnlockStateStore` がアクティブだと `OverlayLockService.start()` は起動をスキップする。
- **想定リスク**: 緊急解除フロー中に再起動すると、一定期間（最大 10 分）オーバーレイが表示されず復帰が弱くなる。
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
- [x] lock_60m（10分短縮で実施。実時間60分は手動検証推奨）。
- [ ] DP ストア不整合（手動削除/書き込み失敗想定）時の復帰挙動。
- [ ] OEM 端末（Samsung / Xiaomi など）での復帰率比較。

---

## 参考
- 調査スクリプト: `tools/reboot_scenarios.sh`
- 実装計画: `/docs/roadmap/implementation_plan.md`
- 既存調査計画: `/docs/research/overlay_reliability_investigation_plan.md`
