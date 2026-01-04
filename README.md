# スマホ依存防止アプリ README（v1.8）

---

## 1. 概要
本アプリはスマホ依存症の克服を支援する **完全ロック型集中モード** を提供する。Lock Task Mode や Device Owner には頼らず、以下の必須権限（オーバーレイ／使用状況アクセス／正確なアラーム／通知〈Android 13 以降〉）を組み合わせたソフトロックでユーザー操作を封じる。Android 12 以降では `SCHEDULE_EXACT_ALARM` を必須化してロック終了・ウォッチドッグを正確に起動する。UI は Jetpack Compose（黒 × 黄色テーマ）で実装し、Android 11 (API 30) 以降をサポートする。詳細なロードマップと評価対象の技術リストは常に [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) を参照すること。  
Supabase 連携は現状オプション扱いで、URL / Key が未設定でもビルド・起動可能（クライアントは生成されず、ログのみ出力）。

- **UI**: `LockScreen` のダイヤルで 1分〜24時間を設定し、残り時間のみを大型表示。開始時に確認ダイアログを挟み誤操作を防止。
- **権限導入**: `PermissionIntroScreen` が Overlay / Usage / 正確アラーム / 通知（Android 13+）を必須として案内し、Android 12 以降は正確アラーム設定画面への遷移ボタンを提供。4 つすべて許可されるまでロック開始ボタンを無効化。
- **状態管理**: `LockScreenViewModel` + `DataStoreManager` + `DirectBootLockStateStore` でロック状態を多層保存し、再起動後も復帰。デバイス保護領域のスナップショットをフォールバックとして保持。
- **サービス構成**: `OverlayLockService` がカウントダウン付きオーバーレイを描画し全画面タッチを食い止める。`LockMonitorService`（Foreground + UsageStats監視）が設定／SystemUI／ホーム／音声アシスタントに加え、インストーラ・主要アプリストアを検知して即座に UI を取り戻す。`WatchdogScheduler` が正確アラームで 3 分毎のハートビートとロック終了アラームを予約し、`ServiceRestartScheduler` で強制終了後の再起動を担保。デバッグビルドではオーバーレイ上に即時解除ボタンを表示。
- **構成管理**: Supabase URL/Key を `local.properties` > BuildConfig 経由で渡せば `SupabaseModule` がクライアントを初期化。未設定でも動作し、ログのみ出力してスキップ（API フローは未実装）。

---

## 2. 現在の実装スナップショット（2026/01/04 時点）
更新根拠: 2026/01/04 通知権限復帰導線の仕様化と監視強化
| フェーズ | ステータス | 現状ハイライト | 次のアクション |
|---------|------------|----------------|----------------|
| 0. 基盤整備 | 🟢 完了 | Compose / Hilt / Navigation の土台を `gradle/libs.versions.toml` と `app/build.gradle.kts` に統合。`./gradlew assembleDebug` が安定。 | Lint / Test の CI 自動化（任意）。 |
| 1. Supabase 設定 | 🟡 任意・無効化可 | BuildConfig から URL / Key を渡せば `SupabaseModule` でクライアント生成。未設定時は `null` を DI しログのみでスキップ。 | 実際の API 実装を行う場合に設定を投入。 |
| 2. 権限導入 | 🟢 完了 | `PermissionIntroScreen` と `DefaultLockPermissionsRepository` が Overlay / Usage / 正確アラーム / 通知（Android 13+）を監視し、いずれか欠けると権限画面を強制表示。Android 12+ は正確アラーム設定画面へ遷移ボタンを提供。 | ロック中に権限や正確アラームが剥奪された際の復帰 UX。 |
| 3. ロック UI + Overlay | 🟢 完了 | `LockScreen` ダイヤル UI（分ダイヤルが時間に連動する不具合を解消）、開始確認ダイアログ、`LockScreenViewModel` の DataStore 連携、`OverlayLockService` のフルスクリーン表示と Direct Boot 保存。デバッグ時のみ即時解除ボタンをオーバーレイに追加。 | Overlay 文言／アクセシビリティ調整、Alarm 連携へ布石。 |
| 4. Foreground 監視 | 🟡 一部 | `LockMonitorService` + `UsageWatcher` が設定・SystemUI・主要ランチャー・音声アシスタント・インストーラ・主要アプリストアを検知し `OverlayManager`/`LockUiLauncher` を発火。`PackageEventThrottler` でデバウンス。 | ActivityManager フォールバックや端末依存差異への追加対策。 |
| 5. 通知ブロック | ⚪ 未着手 | `LockNotificationListenerService` を Manifest 登録のみ。通知ブロックは未実装だが、Android 13+ は `POST_NOTIFICATIONS` をロック必須権限として扱う。 | 通知カテゴリ判定→ `cancelNotification`、通知経由の抜け道封鎖を実装する場合に再度許可を誘導。 |
| 6. AlarmManager 連携 | 🟡 一部 | `WatchdogScheduler` が正確アラームで 3 分毎のハートビートとロック終了アラームを予約し、`BootCompletedReceiver` が CE/DP のスナップショットを見てサービス再起動＆ウォッチドッグ再設定。`ServiceRestartScheduler` で強制終了後も `LockMonitorService` / `OverlayLockService` を再起動。 | WorkManager 自己診断や正確アラーム拒否端末へのフォールバック、解除通知 UX の設計。 |
| 7. テスト & QA | ⚪ 未着手 | テンプレートテストを削除済み（現在テストゼロ）。 | ViewModel / Repository / Service の単体テスト、権限〜ロックの UI テスト、再起動手動検証。 |
| 8. IaC & リリース | ⚪ 未着手 | Terraform / 配布物なし。 | Supabase IaC、Play β 提出物、利用規約・プライバシー整備。 |

---

## 3. 実装済み機能の詳細
### 3.1 LockScreen と状態管理
- `LockScreen.kt` は時間ダイヤル、ロック開始/終了ボタン、残り時間ビューを提供。`LockScreenViewModel` が `DataStoreManager` を監視し `StateFlow` で UI 状態を配信する。
- `startLock()` で権限チェック（通知含む）を行い、ロック開始/終了時刻を算出して DataStore・Direct Boot・Device Protected DataStore へ書き込んだ上で Watchdog ハートビートとロック終了アラームを予約する。
- ロック中は `OverlayLockService` / `LockMonitorService` を起動し、解除時には両サービスと Watchdog を停止。Direct Boot ストア経由で端末再起動後も残り時間を復元できる。

### 3.2 権限導入フロー
- `PermissionIntroScreen` は Overlay / Usage / 正確アラーム / 通知（Android 13+）の 4 権限それぞれに説明＋許可導線を表示し、`LifecycleEventObserver` で復帰時に再評価する。Android 12+ は正確アラーム専用の設定インテントを優先。
- `DefaultLockPermissionsRepository` が `Settings.canDrawOverlays` / `AppOpsManager` / `AlarmManager.canScheduleExactAlarms()` / `POST_NOTIFICATIONS` を用いて権限状態をポーリングし、`UltraFocusApp` が NavHost を Permission→Lock の 2 画面に制御する。

### 3.3 オーバーレイと UsageStats 監視
- `OverlayLockService` は Foreground 通知 + フルスクリーン `WindowManager` オーバーレイで残り時間を表示し、端末ロック時は Device Protected ストアを参照する。Credential Encrypted ストアが読めない場合は DP スナップショットへフォールバックしつつ継続し、タスクキル時は `ServiceRestartScheduler` で再起動を予約。起動直後に foreground を即時降格する短時間FGS運用で通知シェードに残りにくくしている。`formatLockRemainingTime()` を用いて 1 秒ごとに更新。
- `LockMonitorService` は WakeLock と Foreground 通知で常駐し、`UsageWatcher`（UsageStats API）を 750ms 間隔でポーリング。`SettingsPackages` に該当する（設定／SystemUI／ホーム／音声アシスタント／インストーラ／主要ストア）アプリが前面に来た際は `OverlayManager.show()` と `LockUiLauncher.bringToFront()` で自アプリを復帰させる。Foreground は dataSync 種別のみを使用し、起動直後に降格する。
- `PackageEventThrottler` で Overlay 再描画と再起動をデバウンスし、`ServiceRestartScheduler` が `LockMonitorService` / `OverlayLockService` の強制終了後再起動を共通化。
- オーバーレイは全画面タッチを食い止め、カットアウト領域まで覆うレイアウトを採用。デバッグビルドのみ赤い即時解除ボタンを表示する。
- `UsageStatsForegroundAppEventSource.collectRecentEvents` は SecurityException などを握りつぶし、監視が落ちないよう防御的にラップしている。
- `DefaultLockPermissionsRepository` は AppOps 変化を即時検知し、Overlay/Usage/正確アラームが変わった瞬間に状態を再評価する。

### 3.4 再起動・復旧
- `BootCompletedReceiver` は `ACTION_BOOT_COMPLETED / LOCKED_BOOT_COMPLETED / USER_UNLOCKED / MY_PACKAGE_REPLACED` を受け、CE が読めない場合は DP スナップショットにフォールバック。ロックが有効ならサービス再起動とウォッチドッグ再設定を行い、無効ならウォッチドッグをキャンセルする。
- `WatchdogScheduler` + `WatchdogReceiver` が正確アラームで 3 分毎のハートビートを発火し、`LockMonitorService` / `OverlayLockService` を再起動。ロック終了時刻に合わせた正確アラームで自動解除し、権限がない端末では inexact アラームにフォールバック。
- `DirectBootLockStateStore` は Device Protected Storage 上の `SharedPreferences` でロック状態を即時同期し、ユーザー未解錠でもカウントダウンが継続できる。

### 3.5 Supabase 構成（現在は任意）
- `SupabaseConfigRepository` が `local.properties` の `SUPABASE_URL` / `SUPABASE_ANON_KEY` を読み込み、`SupabaseModule` が設定が揃っていれば `SupabaseClient` を生成。未設定なら `null` を返し、アプリは Supabase なしで動作。
- Supabase Auth / API 呼び出しは未実装。将来導入時に設定を追加すれば復帰できる。

---

## 4. ディレクトリとモジュール早見表
| パス | 役割 |
|------|------|
| `app/src/main/java/jp/kawai/ultrafocus/ui/screen` | Compose 画面 (`LockScreen`, `PermissionIntroScreen`) 。 |
| `app/src/main/java/jp/kawai/ultrafocus/ui/lock` | `LockScreenViewModel` と UI 状態定義。 |
| `app/src/main/java/jp/kawai/ultrafocus/service` | `OverlayLockService`, `LockMonitorService`, `UsageWatcher`, `LockUiLauncher`, `PackageEventThrottler`, `WatchdogScheduler`, `ServiceRestartScheduler` 等のサービス群。 |
| `app/src/main/java/jp/kawai/ultrafocus/data/datastore` | `DataStoreManager`, `DirectBootLockStateStore`, デバイス保護領域のプリファレンス。 |
| `app/src/main/java/jp/kawai/ultrafocus/data/repository` | Lock/Permission リポジトリ、`SettingsPackages` 定義。 |
| `app/src/main/java/jp/kawai/ultrafocus/config` | Supabase 設定モデルとリポジトリ（未設定ならクライアント非生成）。 |
| `app/src/main/java/jp/kawai/ultrafocus/di` | Hilt Module 群（DataStore / Repository / Service / Supabase）。 |
| `app/src/main/java/jp/kawai/ultrafocus/navigation` | `AppDestination` など NavHost 用定義。 |
| `app/src/main/java/jp/kawai/ultrafocus/receiver` | `BootCompletedReceiver`, `WatchdogReceiver`。 |
| `app/src/main/res` | Strings / Colors / Themes（黒 × 黄色）と権限文言。 |

---

## 5. ロックフロー（高レベル）
1. アプリ起動時に `UltraFocusApp` の NavHost がロードされ、必須 4 権限（Overlay / Usage / 正確アラーム / 通知〈Android 13+〉）のいずれかが未許可なら `PermissionIntroScreen` を表示。
2. すべて許可されると `LockScreen` に遷移し、ユーザーはダイヤルで 1分〜24時間を設定。`LockScreenViewModel` が選択値を DataStore に保存。
3. 「ロック開始」で `LockScreenViewModel.startLock()` がロック開始/終了時刻を記録し、`OverlayLockService` / `LockMonitorService` を起動。`WatchdogScheduler` が 3 分ごとのハートビートと終了時刻の正確アラームを予約する。
4. `OverlayLockService` がフルスクリーンオーバーレイを描画し、残り時間を 1 秒ごとに更新しながら Foreground 通知でも表示。
5. `LockMonitorService` + `UsageWatcher` が設定アプリを検知すると、`OverlayManager` でオーバーレイを再掲出し `LockUiLauncher` でアプリを前面に戻す。`PackageEventThrottler` と `ServiceRestartScheduler` により過剰な再描画やタスクキル後の再起動を制御。
6. 端末再起動／アプリ更新／強制終了時は `BootCompletedReceiver` と `WatchdogReceiver` がサービス再起動・ロック解除アラーム再設定を行い、Direct Boot データから残り時間を復元。

---

## 6. セットアップ & ビルド
### 6.1 前提
- Android Studio Koala 以上、JDK 17。
- テスト端末は Android 11 以降。開発中はオーバーレイ・使用状況アクセスを手動で許可。

### 6.2 Supabase 設定（任意・ソース管理禁止）
設定を入れる場合は `local.properties` に以下を追加（未設定でもビルド・起動可能）：
```
SUPABASE_URL=https://example.supabase.co
SUPABASE_ANON_KEY=your-anon-key
```
`BuildConfig` 経由で `SupabaseModule` に渡される。未設定または空の場合は Supabase クライアントを生成せずスキップする。

### 6.3 ビルド / 実行コマンド
- `./gradlew assembleDebug` : デバッグ APK ビルド。
- `./gradlew installDebug` : 接続端末へインストール。
- `./gradlew lint` : Compose / Android Lint。
- `./gradlew testDebugUnitTest` : Kotlin 単体テスト。
- `./gradlew connectedAndroidTest` : UI / 計測テスト（要デバイス）。

---

## 7. テスト & 品質計画
- 現状、自動テストは未配置（テンプレートを削除済み）。`PackageEventThrottler`, `LockScreenViewModel`, `UsageWatcher` など副作用の大きいロジックに対する単体テストをフェーズ 7 で整備する。
- フォアグラウンドサービスやオーバーレイは端末依存差異が大きいため、Pixel / Galaxy / Xperia での抜け道検証をテスト項目に含める。
- バッテリーインパクトや `WakeLock` 維持時間の計測、Sentry 等の監視は `IMPLEMENTATION_PLAN.md` の「実装したい技術リスト」を参照してチケット化する。

---

## 8. 未完了タスク（抜粋）
1. **Foreground 監視強化**（フェーズ4）: SystemUI / Play ストア / Assistant をブラックリスト化し、UsageStats 非対応端末向けに `ActivityManager` フォールバックを追加。動的ホワイト/ブラックリストを `LockRepository` で管理。
2. **通知ブロック実装（任意）**（フェーズ5）: 将来 `LockNotificationListenerService` でカテゴリ判定→ `cancelNotification` を実施し、通知経由の設定遷移を検知して `LockUiLauncher` と連携する場合に再度対応。
3. **正確アラーム運用の検証**（フェーズ6）: `SCHEDULE_EXACT_ALARM` を拒否した端末でのフォールバック（WorkManager/JobScheduler 自己診断、ユーザー通知）、OEM ごとの Doze/アラーム抑制影響の計測、解除時の UX を磨く。
4. **テスト & メトリクス**（フェーズ7）: ViewModel / Repository / Service のユニットテストと Compose UI テスト、Usage 監視遅延や Overlay/Watchdog 発火回数の計測ログを整備。
5. **IaC / リリース準備**（フェーズ8）: Supabase を Terraform 化し、Play β 提出物（スクリーンショット、利用規約、プライバシー）を整える。

詳細は常に [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) の該当フェーズを参照し、PlantUML 図や高度テストが必要な場合は要事前相談。

---

## 9. バージョン履歴
| バージョン | 変更内容 | 日付 |
|-----------|----------|------|
| v1.1 | 初版（Lock Task Mode 前提） | 2025/10/21 |
| v1.2 | Lock Task + AlarmManager 方針、法的ポリシー追記 | 2025/10/22 |
| v1.3 | 3 権限方式への転換、2 画面構成を導入（後に通知は任意化したが現在は通知権限必須） | 2025/11/03 |
| v1.4 | README を現行実装（Overlay/Usage 監視、Direct Boot 復旧、Supabase 構成、今後の課題）に合わせて全面更新 | 2025/11/12 |
| v1.5 | Supabase を任意化（未設定でも起動可）、テンプレートテスト削除、README を現状に合わせ更新 | 2025/11/22 |
| **v1.6** | ダイヤル連動バグ修正、ロック開始確認ダイアログ追加、オーバーレイのタッチ食い止め＋デバッグ解除ボタン、ブート後復帰の確実化、UsageWatcher 例外防御 | **2025/11/22** |
| **v1.7** | 権限変更の即時検知を追加、監視対象をインストーラ・主要アプリストアまで拡張 | **2025/11/24** |
| **v1.8** | 正確アラーム権限を必須化し PermissionIntro を更新。Watchdog ハートビートとロック終了アラーム、サービス再起動スケジューラ、CE/DP フォールバックを追加して再起動復旧を強化 | **2025/11/30** |

---

## 10. 参考ドキュメント
- [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md): フェーズ別タスク、技術リスト、抜け道封鎖メモ。
- [`README.md`](README.md) 本文: 現行実装のサマリ（本ファイル）。
- `app/src/main/java` 配下の各パッケージコメント: 主要クラスの実装詳細。
