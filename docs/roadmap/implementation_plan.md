# スマホ依存防止アプリ 実装計画（v1.6）

---

## 🎯 プロジェクト概要

本アプリは、スマホ依存症を克服するための **完全ロック型集中支援アプリ** である。  
**Lock Task Mode は一切使用せず、4権限方式（Overlay／Usage／正確アラーム／通知〈Android 13+〉）** によって  
「実質的に完全なロック体験」を提供する。Android 12 以降は `SCHEDULE_EXACT_ALARM` を必須化し、Android 13 以降は `POST_NOTIFICATIONS` を必須化する。通知ブロックは今後拡張予定の任意機能。

現行ロック構成：
- 🧩 **オーバーレイ表示（Overlay）**：画面全体を覆い操作を封じる
- 📊 **使用状況アクセス（UsageStats）**：許可外アプリを検知して即ブロック
- ⏰ **正確アラーム**：ロック終了時刻とウォッチドッグを `AlarmManager.setExactAndAllowWhileIdle()` で予約し、再起動やタスクキル後も復元

---

## 実装したい技術リスト
(ふさわしい場面があったら事前通知する。基本的にチケットにまとめる必要があるため)
- クラス図、ER図、状態遷移図を適切な場面で作成する。PlantUMLで作成
- 異常系を理解し、エラーパターンを洗い出し、適切なエラーハンドリングを設計し、実装する
  - 起こり得る異常系を洗い出して、正常なエラー、異常なエラー、想定外のエラーに分ける
  - 正常なエラー、異常なエラー、想定外のエラーそれぞれに適切な処理を規定し、実装する
  - エラーの洗い出し、取り決めは、事前に検討なり設計なりした結果のチケット、ドキュメント、コメントなどが必要
  - エラーログ、エラーメッセージの内容も見られる
  - Sentryとかでエラーの監視もすると良い
- テストを理解し、テストケースの設計や、単体テストの作法や手法に則った単体テストの実装などができる
  - テストケースが洗い出され、網羅性が担保されている
  - カバレッジC1 100%など、同値分割、境界値分析
  - 単体テストで良い
  - APIの疎通確認レベルでは甘い
- 排他制御やアトミック操作などを利用して、(スレッド)セーフな非同期処理が書ける
  - 安全で適切な非同期処理（スレッドセーフ）
  - チケットになぜそのような設計にしたのか残す。必要のない非同期処理はだめ
  - キーワード: 非同期処理、アトミック操作、共有メモリ等のプロセス間通信、データベースのロック
- 適切な手段でパフォーマンスを測定・分析し、ボトルネックの有無を確認したり、ボトルネックを特定したりすることができる
  - パフォーマンスを測定する
  - ボトルネックの有無を判断する
  - 原因を切り分けて特定する
  - 解決策を立てる。解決できたことを立証する。再度パフォーマンスを測定する
- パフォーマンスのボトルネックに対し、改善策を複数立案し、最善な実装を行い、パフォーマンスの改善ができたことを検証できる

## 🧭 開発全体フロー（フェーズ構成）

| フェーズ | 内容 | 目的 |
|-----------|------|------|
| 0 | 基盤整備 | プロジェクト起動と依存管理 |
| 1 | 設定値注入 & Supabase接続 | BuildConfig / Supabase 初期化 |
| 2 | **権限導入フロー実装** | Overlay / Usage / 正確アラーム / 通知（Android 13+）の権限誘導 |
| 3 | **ロック画面UI + Overlay実装** | ダイヤルUI＋残り時間＋Overlay表示 |
| 4 | **Foreground監視 + UsageStats監視** | 許可外アプリ検出と即時Overlay |
| 5 | **通知ブロック連携** | NotificationListenerによる通知遮断（将来拡張、現状任意）※通知権限は必須 |
| 6 | AlarmManager連携 | 自動解除・再起動復帰 |
| 7 | テスト & 品質保証 | 権限挙動／再起動時ロック再現確認 |
| 8 | IaC & リリース | Terraform / β版公開 |


## 🚦 実装進捗サマリ（2026/01/18 時点）
更新根拠: 2026/01/18 dp_corrupt_missing_end / force-idle 併用 / cold_boot の追加検証結果と実機記録テンプレート・追加シナリオ（解錠タイミング差分含む）を反映

| フェーズ | ステータス | 現状ハイライト | 次のタスク |
|---------|------------|----------------|------------|
| 0 | 🟢 完了 | gradle/libs.versions.toml と `app/build.gradle.kts` で Compose／Hilt／Navigation の土台が揃い、`./gradlew assembleDebug` が安定動作 | CI で lint/test を自動化（任意） |
| 1 | 🟢 完了 | BuildConfig 経由で Supabase URL/Keys を注入し、`SupabaseModule` と `UltraFocusApplication` でクライアントを初期化済み | API/認証ワークフロー本体は未実装のため、要件定義と併せて設計 |
| 2 | 🟢 完了 | `PermissionIntroScreen` + `DefaultLockPermissionsRepository` が Overlay / Usage / 正確アラーム / 通知（Android 13+）を監視し、いずれか未許可なら NavHost を Permission 画面に固定。Android 12+ 用の正確アラーム設定導線を追加。 | ロック中に権限/正確アラーム/通知が剥奪された際の強制復帰 UI を追加 |
| 3 | 🟢 完了 | `LockScreen` ダイヤル・`LockScreenViewModel`・`OverlayLockService`＋DataStore/DirectBoot 保存で基本ロック体験を提供。ロック中のみ露出する緊急解除ボタンから宣言文全文入力チャレンジで解除できるフローを追加。緊急解除状態を永続化し、権限画面/監視の干渉でも導線を維持するよう補強。ロック中は MainActivity の UI を描画せずオーバーレイ優先に切り替え。 | Overlay UI の仕上げと Alarm 連携をフェーズ6で進める |
| 4 | 🟡 一部実装 | `LockMonitorService` + `UsageWatcher` が設定系に加え SystemUI/ランチャー/音声アシスタント/インストーラ/主要ストアを検知し Overlay/Lock UI を再表示。`PackageEventThrottler` でデバウンス。ロック中は既定ダイヤラ/SMSのみ例外許可し、オーバーレイにショートカットを配置。許可アプリ遷移のちらつきを抑止。 | SystemUI 以外の抜け道の網羅、ActivityManager フォールバック、動的ホワイト/ブラックリスト |
| 5 | 🔴 未着手 | Manifest に `LockNotificationListenerService` を宣言しただけで、通知キャンセル処理は空実装 | 通知キャンセル／許可リスト／通知経由の設定遷移ブロック |
| 6 | 🟡 一部実装 | `WatchdogScheduler` が正確アラームでハートビートとロック終了アラームを予約し、`BootCompletedReceiver` が CE/DP スナップショットを突き合わせてサービス再起動・ウォッチドッグ再設定。`ServiceRestartScheduler` でサービス強制終了後の再起動を統一。FGS 起動例外（startForegroundService 未達）対策を追加。 | WorkManager 自己診断、正確アラーム拒否端末へのフォールバック、解除通知 UX・ログ計測の整備 |
| 7 | 🟡 一部実装 | `ServiceRestartSchedulerTest` / `BootFastStartupReceiverTest` を追加し、エミュレータで再起動シナリオ（lock_immediate/30s/3m/10m + no_lock / lock_end_before / lock_end_after / MY_PACKAGE_REPLACED / lock_60m実時間60分待機・ロック設定1時間5分）と通知権限OFFの復帰、Doze強制30分、Exact Alarm拒否を確認。追加で battery_saver_on / screen_off_long / lock_60m_no_reboot / lock_90m / lock_120m / time_shift を実施（短縮・時間シフトあり）。`dp_corrupt_missing_end` / force-idle 併用 / cold_boot の再検証を追加。 | 実時間での長時間継続、OEM差分、時刻変更の実機再検証 |
| 8 | 🔴 未着手 | IaC・Play 配布体制は未整備 | Terraform で Supabase 管理、β版申請のドキュメント／手順策定 |

---

## 🚀 Play 公開最短チェックリスト（追加実装なし）
機能追加は後回しにし、現状コードで最短リリースするための必須タスクのみを列挙する。

1. **ID/バージョン確定**: `app/build.gradle.kts` で `applicationId` を本番ドメインに変更し、初回リリースの `versionCode` / `versionName` を設定。
2. **アイコン差し替え**: アダプティブアイコンを `mipmap-anydpi-v26/ic_launcher.xml` ほかへ配置し、デフォルトロックアイコンを除去。
3. **署名体制**: Play App Signing を有効化し、アップロード鍵を新規作成。ローカルで `./gradlew bundleRelease` が通ることを確認（JDK17）。
4. **ストア資材**: 最低限のスクリーンショット（ロック画面・権限導入）、フィーチャーグラフィック、日英の短文/長文説明を準備。
5. **ポリシー/申告**: 公開URL付きプライバシーポリシーを用意し、Data Safety フォームは「収集・共有なし」を申告。特別権限（SYSTEM_ALERT_WINDOW / PACKAGE_USAGE_STATS / FOREGROUND_SERVICE* / WAKE_LOCK）の利用目的を明文化して申請。
6. **手動検証（最小限）**: 実機で「権限付与→ロック開始→再起動復帰→ロック解除」を確認し、Pixel と Samsung など 2 機種で Overlay/UsageStats が動作するかを目視チェック。
7. **提出フロー**: 内部テストトラックへ AAB をアップロードし、Pre-launch Report の警告を確認。問題なければクローズド→公開へ進める。リリースごとに `versionCode` をインクリメント。

---


## 🧩 フェーズ詳細

### フェーズ0：基盤整備
- Android Studio プロジェクト初期化
- Gradle version catalog (libs.versions.toml)
- Hilt 導入（DI構築）
- Jetpack Compose Navigation 導入

**現状（2026/01/10）**
- `app/build.gradle.kts` と `libs.versions.toml` により Compose / Material3 / Navigation / Hilt を統一管理し、`./gradlew assembleDebug` が手元で完走する状態。

**残タスク**
- CI 上での lint/test 自動実行や依存アップデートの定期運用は未整備（必要になり次第チケット化）。

---

### フェーズ1：設定値注入 & Supabase接続
- local.properties → BuildConfig
- Supabaseクライアント生成（URL・AnonKey）
- HiltによるDI統合

**現状（2026/01/10）**
- `app/build.gradle.kts` から `SUPABASE_*` を BuildConfig に注入し、`SupabaseModule` + `SupabaseConfigRepository` で `SupabaseClient` を初期化。`UltraFocusApplication` / `MainActivity` で起動時チェックまで実装済み。

**残タスク**
- 認証・データ同期など Supabase 本体のユースケースは未実装。スキーマ設計と API 呼び出し層を別途整える必要あり。

---

### フェーズ2：権限導入フロー（Overlay / Usage / 正確アラーム / 通知）
目的：必須4権限（Overlay / Usage / 正確アラーム / 通知〈Android 13+〉）の許可誘導をUX化  
タスク：
- **PermissionIntroScreen** 実装
  - 各権限ごとにボタン＋状態表示
  - 権限未許可なら起動時に毎回表示
- 権限状態を `PermissionRepository` で管理（Flow化）

**現状（2026/01/10）**
- `PermissionIntroScreen` と `DefaultLockPermissionsRepository` が Overlay/Usage/正確アラーム/通知（Android 13+）の 4 権限を監視し、`UltraFocusApp` の `NavHost` が許可完了までは Permission 画面に固定される。Android 12+ は正確アラーム設定インテントを優先表示。
- `LockScreenViewModel` が `permissionState` を購読し、4 権限のいずれかが欠けるとロック開始ボタンを無効化する。
- AppOps 監視と Watchdog により通知権限の変更を再評価する。

**残タスク**
- ロック中に権限や正確アラーム/通知が剥奪された場合の強制復帰 UI、再許可導線をサービス側に組み込む。
- 権限説明の追加要素（動画、FAQ など）が必要なら別途検討。

---

### フェーズ3：ロック画面UI + Overlay実装
目的：ユーザーが直感的にロックできるUI  
タスク：
- ダイヤル／スライダーUIで時間選択（1分〜24h）
- 「ロック開始」ボタン（黄色強調）
- OverlayService 起動（黒背景＋残り時間表示）
- DataStore に設定時間を保存

**現状（2025/12/07）**
- `LockScreen`（ダイヤルUI）＋ `LockScreenViewModel` が DataStore を介して時間設定とロック状態を管理し、`OverlayLockService` が全画面オーバーレイとカウントダウンを表示。タッチ無効化・Direct Boot 対応の保存 (`DirectBootLockStateStore`) まで完了し、CE（Credential Encrypted）未解錠時は DP スナップショットへフォールバックして表示を継続する。

**残タスク**
- ロック残り時間の正確性を OS レベルで担保するため、`AlarmManager`/WorkManager との連携はフェーズ6で要実装。
- Overlay UI のデザイン調整（警告文、解除禁止メッセージ、アクセシビリティ）や、例外経路の UX は未検討。

---

### フェーズ4：Foreground監視 + UsageStats監視
目的：アプリ外操作の封鎖  
タスク：
- ForegroundService 常駐（START_STICKY + WakeLock）
- UsageStatsManager で MOVE_TO_FOREGROUND を監視
- 許可外アプリ（設定/Play/インストーラ等）→ 即Overlay再表示

**現状（2025/12/07）**
- `LockMonitorService` が ForegroundService + WakeLock で常駐し、`UsageWatcher` (`UsageStatsForegroundAppEventSource`) が 750ms 間隔で前面アプリを監視。`SettingsPackages` は設定/permission controller に加え SystemUI／主要ランチャー／音声アシスタント／インストーラ／主要ストアを含み、検知時に `OverlayManager.show()` と `LockUiLauncher.bringToFront()` を呼び出す。
- `PackageEventThrottler` で Overlay/Lock UI の再描画をデバウンスし、`ServiceRestartScheduler` がタスクキル時の再起動を担保。UsageStats 例外は握りつぶして監視停止を防ぐ。
- foregroundServiceType は Play/実行リスクを避けるため `dataSync` に統一（`SPECIAL_USE` は使用しない）。起動直後に foreground を降格する短時間FGS運用で、通知シェード残留を抑制。

**残タスク**
- UsageStats で拾えない端末向けに `ActivityManager` などのフォールバックを追加し、動的ホワイト/ブラックリスト管理を `LockRepository` へ移す。
- 監視遅延・バッテリー影響の計測、主要抜け道（SystemUI/Play/Assistant 等）の網羅テストとメトリクス整備。

---

### フェーズ5：通知ブロック連携（任意・将来拡張）
目的：通知経由の抜け道封鎖（現行ビルドでは未使用）  
タスク：
- NotificationListenerService 実装
- ロック中は全通知を `cancelNotification(sbn.key)`
- 必要に応じて「重要通知のみ許可」フィルタを設計

**現状（2025/12/07）**
- Manifest に `LockNotificationListenerService` を宣言し、クラスも定義したが中身は空で通知をキャンセルしていない。

**残タスク**
- NotificationListener で取得した通知をカテゴリ判定し、ロック中は既定ホワイトリスト以外を `cancelNotification` する処理を実装。
- 通知経由で設定アプリへジャンプする経路を検知して `LockUiLauncher` にリダイレクトする連携、および権限剥奪時の復帰通知を設計。

---

### フェーズ6：AlarmManager連携
目的：時間経過で自動解除 & 再起動復帰  
タスク：
- setExactAndAllowWhileIdle() で解除時刻を予約
- BOOT_COMPLETED Receiver で再起動後の再ロック

**現状（2025/12/07）**
- `BootCompletedReceiver` が `LOCKED_BOOT_COMPLETED / USER_UNLOCKED / BOOT_COMPLETED / MY_PACKAGE_REPLACED` を受信し、CE が欠損しても DP スナップショットにフォールバック。ロックが有効ならサービス再起動とウォッチドッグ再設定を行い、無効ならウォッチドッグを停止。
- `WatchdogScheduler` + `WatchdogReceiver` が正確アラームで 3 分毎のハートビートとロック終了アラームを予約し、プロセスが殺されても Overlay/Monitor を再起動・終了時は自動解除。権限が無い端末では inexact アラームにフォールバック。
- `ServiceRestartScheduler` が `LockMonitorService` / `OverlayLockService` のタスクキル時再起動を共通化。`OverlayLockService` は DP スナップショットを保持し、ユーザー解錠後に CE へ切り替えて残り時間を復元。

**残タスク**
- 正確アラームを拒否する端末や Doze 厳格端末向けの WorkManager/JobScheduler ベース自己診断を追加し、復旧可否をロギング。
- 正確アラーム許可が外れた際の再誘導 UX、解除予定のユーザー通知／Telemetry（ハートビート成功率、解除アラーム実行可否）の整備。

---

### フェーズ7：テスト & 品質保証
目的：抜け道なく安定稼働  
タスク：
- 主要機種（Pixel / Galaxy / Xperia）で権限・再起動挙動を検証
- UIテスト：権限導入→ロック→解除
- 手動テスト：Overlay・UsageStats・Notification連携の遅延確認

**現状（2026/01/16）**
- `ServiceRestartSchedulerTest` と `BootFastStartupReceiverTest` を追加し、再起動復帰のエミュレータシナリオを実施済み。`PackageEventThrottler` や `LockScreenViewModel` などコアロジックのテストは未作成。

**残タスク**
- ViewModel/Repository/Service/Receiver の単体テスト整備、Compose UI / Navigation の UI テスト、実機での再起動・権限テスト計画を策定。
- バッテリー/パフォーマンス計測、Sentry 等の監視設定を検討。

---

### フェーズ8：IaC & リリース
目的：環境管理とPlay β公開  
タスク：
- Terraformで Supabase Auth/Storage IaC化
- Google Play β版申請ビルド
- 利用規約・プライバシーポリシー整備

**現状（2025/12/07）**
- IaC・配布準備・ストア用ドキュメントは未着手。

**残タスク**
- Supabase/Terraform のモジュール化、CI での署名・リリースビルド自動化、Play Console 提出物（スクリーンショット/ポリシー文書）の作成。

---

## 🧠 実装方針メモ

- **Lock Task Mode は完全撤廃**（Device Owner 不要）
- **権限方式**で制御：Overlay + UsageStats + 正確アラーム + 通知（Android 13+）。通知ブロックは任意の将来拡張。アクセシビリティ権限とデバイス管理者は採用しない。
- ViewModel: ロック状態/残り時間/権限状態を StateFlow 管理
- Repository層で Supabase / DataStore / OverlayManager を抽象化（AlarmManager 連携はフェーズ6で追加予定）
- UI層：Scaffold + NavHost + BottomNavigation
- テーマ：黒×黄色（Material3）

---

## 🔍 リスクと対策

| リスク | 対策 |
|--------|------|
| 権限の剥奪 | `DefaultLockPermissionsRepository` が Overlay/Usage/正確アラームを監視し、欠けたら即 PermissionIntro へ戻す |
| 再起動・アプリアップデート後の漏れ | `BootCompletedReceiver` が CE/DP スナップショットからサービスを再起動し、Watchdog を再設定 |
| 強制終了・Doze | ForegroundService + `ServiceRestartScheduler` + Watchdog ハートビートで再起動を担保 |
| 通知からの抜け道 | NotificationListener 実装で遮断予定（未実装、今後対応） |
| 権限/正確アラーム拒否 | 権限誘導 UI と設定画面への導線。フォールバックと再誘導をフェーズ6で検討 |
| バックアップでロック状態が復元/漏洩する恐れ | Play 公開前にバックアップルールを策定し、ロック状態・Direct Boot スナップショットをクラウドバックアップ対象外にする |

---

## 🔐 抜け道封鎖の実装状況（2025/12/07 時点）

### 実装済み
- `LockMonitorService` + `UsageWatcher` が Settings/permission controller に加え SystemUI／主要ランチャー／音声アシスタント／インストーラ／主要ストアを検知すると `OverlayManager.show()` と `LockUiLauncher.bringToFront()` を呼び、即座にロック UI を再前面化する。
- ForegroundService 化、WakeLock 取得、`PackageEventThrottler` と `ServiceRestartScheduler` による再描画/再起動のデバウンス処理。
- `BootCompletedReceiver` + `DirectBootLockStateStore` + Watchdog ハートビート/ロック終了アラームにより再起動やタスクキル後でもロック状態を復元し、必要なサービスを起動。

### 未実装 / 課題
1. UsageStats で拾えない端末向けの `ActivityManager` フォールバックや、ユーザー任意のホワイト/ブラックリスト設定。
2. NotificationListener による通知経由の抜け道検知と遮断。
3. 抜け道封鎖のメトリクス（検知遅延/復帰回数）とユニットテスト（`PackageEventThrottler`, `LockMonitorService` など）。
4. ロック中の権限剥奪時に即座に復帰導線を出す UI/通知の設計。
5. バックアップ/データ移行時の扱いを定義し、ロック状態を除外するポリシーを追加（`backup_rules` / `data_extraction_rules` の具体化）。

### STEP 2: 設定アプリ検知→強制オーバーレイ

#### 現状（2025/12/07）
- `LockMonitorService` は UsageStats ベースで設定アプリに加え SystemUI / ランチャー / アシスタント / ストア / インストーラまでブラックリスト化し、検知時に Overlay/Lock UI を再掲出。
- `PackageEventThrottler` を本番コードで利用しデバウンス。テストコードやメトリクスは未整備。

#### 次にやること
1. UsageStats が効かない端末向けに `ActivityManager` フォールバックを追加し、SystemUI で拾えないケースを埋める。
2. 動的ホワイト/ブラックリストを `LockRepository` に移し、記録・テレメトリを追加。
3. `PackageEventThrottler`・`LockMonitorService` のユニットテストと計測ログ（検知遅延、デバウンス回数）を追加。
4. 通知経由の設定遷移を検知して Overlay/Lock UI を発火させるフローを NotificationListener と連携。

---

## 📘 バージョン履歴

| バージョン | 更新内容 | 日付 |
|-------------|-----------|------|
| v1.2 | Lock Task Mode前提設計 | 2025/11/01 |
| **v1.3** | Lock Task Modeを完全削除。3権限方式（Overlay／Usage／Notification）へ移行（のちに通知は任意化したが、現在は正確アラームと通知権限を追加した4権限運用） | **2025/11/03** |
| **v1.4** | 正確アラームを必須化し、ウォッチドッグ/サービス再起動計画を追加。CE/DP フォールバック強化を反映 | **2025/11/30** |
| **v1.5** | ドキュメントを 2025/12/01 時点に更新し、バックアップリスクと抜け道課題を追記 | **2025/12/01** |
| **v1.6** | 緊急解除（宣言文全文入力）フロー追加を反映し、進捗日付を 2025/12/07 に更新 | **2025/12/07** |
