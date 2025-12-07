# オーバーレイ消失 調査計画（2025-12-06）

## 背景と目的
- 再起動後しばらくしてオーバーレイが消える事象が継続的に発生。Phase4「Foreground監視 + UsageStats監視」および Phase6「AlarmManager連携」の安定性課題に該当。
- 目的：Doze/メモリ圧・OEM 最適化下でもオーバーレイが継続表示されることを再現性高く保証する。

## 既知の原因（整理）
- Foreground 降格: `OverlayLockService` / `LockMonitorService` 起動直後に `demoteForegroundIfNeeded()` 実行。
- WakeLock 不足: `LockMonitorService` の WakeLock が 15 秒でタイムアウト。`OverlayLockService` は WakeLock 非保持。
- Watchdog 限界: ハートビート 3 分間隔。Doze 遅延・Overlay へ WakeLock 未付与・起動デバウンス 12 秒で復旧チャンスが細い。

## 成功基準（ゴール）
- Pixel / Galaxy など 2 機種で「再起動→ロック開始→画面消灯→Doze 強制→30 分放置」後もオーバーレイが前面維持されること。
- Watchdog ログで「遅延 5 分超」が 0 件、サービス強制終了後の復帰率 100%。

## P0 ホットフィックス施策（先行実装）
1. **Foreground非維持を前提とした安定化**: Foreground 常駐は行わず、降格後の生存性を他手段で補強する。通知は起動中のみ最小限に留め、ユーザーが通知センターから停止しづらい導線を検討（タップ時はステータス表示のみ）。
2. **WakeLock延長/追加**: `WAKE_LOCK_TIMEOUT_MILLIS` を 180_000L へ延長し、`OverlayLockService` に PARTIAL_WAKE_LOCK を追加。取得/解放を try/finally で統一し、重複取得をガード。
3. **Watchdog短周期 + フォールバック**: ハートビートを 60_000ms に変更し、起動直後に初回予約を実施。`setExactAndAllowWhileIdle` 失敗や Doze 遅延に備え、WorkManager/JobScheduler へ同一ジョブを二重化（遅延検出時に次周期を前倒し）。
4. **起動デバウンス調整**: ServiceRestart 経由の復旧時はデバウンスを 0〜3 秒へ短縮。通常起動は現状 12 秒を維持しスパム防止。
5. **ログ整備**: WakeLock 取得/解放、アラーム予約/遅延、サービス startId/stopSelf の理由を `Log.d` で共通フォーマット化し、遅延秒数をメトリクス化。

## P1 追加施策（別チケット化）
- WorkManager/JobScheduler を用いた 1 分間隔の自己診断（Doze 厳格端末対策）。
- バッテリー最適化除外ガイド: 初回ロック時に設定画面へ誘導し、状態を保存して再提示を抑制。
- UsageStats 非対応端末向けフォールバック（ActivityManager）と動的ブラック/ホワイトリストの `LockRepository` への移管。
- テレメトリ/Sentry 連携: アラーム失敗率・WakeLock 取得失敗を収集。

## 検証計画
- **デバイス**: Pixel (AOSP 近似), Samsung OneUI。Doze 強制は `adb shell cmd deviceidle force-idle` を使用。
- **シナリオ**: 再起動後ロック開始→画面消灯→Doze 強制→10/30 分放置→ロック解除→再起動復帰確認。
- **観測**: `logcat | grep "OverlayLockService\\|LockMonitorService\\|Watchdog"` で WakeLock/アラーム遅延、サービス復帰経路を確認。遅延秒数と復帰結果を表で記録。
- **回帰**: ロック解除フロー、権限剥奪時の復帰導線、通知表示有無を手動確認。

## リスクと緩和
- バッテリー消費増: WakeLock を延長しつつタイムアウト付きで取得し、取得回数と保持時間をログ化して調整。
- Foreground非維持による停止リスク: 二重化したアラーム/WorkManager と WakeLock で復旧率を担保。最適化除外ガイドでさらに緩和。
- 通知UX: 起動中のみ最小文言で表示し、タップ時は停止でなく状態表示に遷移させる。
- OEM 最適化: フォールバック経路（WorkManager）と最適化除外ガイドで緩和。

## 成果物
- P0 実装 PR（サービス常時FG化 + WakeLock 延長/追加 + Watchdog 60 秒化 + ログ追加）。
- 検証ログ/表、再現手順ドキュメント。
- P1 以降の課題チケット（WorkManager フォールバック、ActivityManager 対応、Sentry 設定）。

## 最新レビュー結果（2025-12-06）
- [重大] Direct Boot 中の WorkManager 呼び出しで `IllegalStateException` リスクあり：BootFastStartupReceiver/BootCompletedReceiver/WatchdogReceiver から `WatchdogWorkScheduler.schedule()` を実行しており、未解錠端末では WorkManager 初期化に失敗する可能性。**対策**: `UserManager.isUserUnlocked` で解錠後のみ WorkManager を起動し、未解錠時は Alarm のみで心拍を維持するフォールバックを追加する。
- [中] ロック解除後も WorkManager 心拍が再登録され続ける：`WatchdogWorker` が `lockActive == false` でも再スケジュールしており、解除後に1分間隔の不要ジョブが残留。**対策**: 非ロック時は再スケジュールをスキップするか `WatchdogWorkScheduler.cancel()` を呼ぶ。
- [軽微] 再起動復旧時のデバウンス短縮が未適用：`LockMonitorService` の `START_DEBOUNCE_MILLIS` が 12 秒のまま。**対策**: ServiceRestart 経由の起動時のみ 0〜3 秒へ短縮する分岐を入れる。

## 追加アクション（次のPRで対応）
1. Direct Boot ガード: WorkManager 呼び出し箇所に `isUserUnlocked`/try-catch を追加し、未解錠時は Alarm のみでハートビートを継続。
2. 心拍再登録の抑制: `WatchdogWorker` でロック非アクティブ時の再スケジュールを停止し、解除完了後は `cancel()` でジョブを消す。
3. デバウンス調整: `LockMonitorService` 起動デバウンスを再起動復旧経路だけ短縮する実装を追加し、P0 計画と揃える。
4. 検証: JDK 17 でビルド環境を整えた上で、Doze 強制 30 分放置シナリオを再実施し、Alarm/WorkManager の遅延ログと復旧可否を確認。

## 最新テスト結果（2025-12-07）
- ツール: `tools/reboot_scenarios.sh all`（ログ: `reboot_20251207_162351.log`）。ロック時間を20分に延長し、lock_10mは再起動まで約9分待機。
- 観測:
  - `no_lock` は WorkManager 未スケジュール/即キャンセルで PASS。
  - `lock_immediate`/`lock_30s`/`lock_3m` は DP に `is_locked=true` を保持し、`LockMonitorService`/`OverlayLockService` が再起動直後に起動（ActivityManagerログで確認）。
  - `lock_10m` 再起動後に `WatchdogWorker` が実行され、`ForegroundServiceStartNotAllowedException`（未解錠状態での FGS 起動拒否）が発生。WorkManager に残ったジョブが Direct Boot 中に走ったことが原因。
- 追加で必要な対策（緊急度高）:
  1) ブート完了時にユーザ未解錠なら `WatchdogWorkScheduler.cancel()` で既存ジョブを消す。
  2) `WatchdogWorker` 内で `UserManager.isUserUnlocked` をチェックし、未解錠なら FGS 起動をスキップして Alarm フォールバックのみ継続。
  3) `BootFastStartupReceiver` / `BootCompletedReceiver` から WorkManager を起動する際も、未解錠時は確実にスキップ（現行コードは新規スケジュールを避けているが、残存ジョブのキャンセルが未実装）。
  4) テストスクリプトの判定ロジックを緩和し、ロック時は「サービス起動が見えること」を主要判定、WorkManager スケジュールの有無は参考にする。
