# smartphone-lock-app

スマホの利用を一定時間ロックして集中を支援する Android アプリです。

## Features

- Jetpack Compose ベースのロック時間設定 UI（1分〜24時間）
- オーバーレイ + 使用状況アクセスによるロック画面復帰
- 正確アラームと再起動復旧（ウォッチドッグ）

## Requirements

- Android Studio (Koala 以降推奨)
- JDK 17
- Android 11+ (API 30+)

## Setup

1. リポジトリをクローン
2. 必要なら `local.properties` に設定を追加
3. ビルド

```bash
./gradlew assembleDebug
```

## Optional Config

Supabase を使う場合のみ `local.properties` に追加します。

```properties
SUPABASE_URL=https://example.supabase.co
SUPABASE_ANON_KEY=your-anon-key
```

未設定でもアプリは起動できます。

## Development Commands

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew lint
./gradlew testDebugUnitTest
./gradlew connectedAndroidTest
```

## Project Structure

- `app/` : Android アプリ本体
- `gradle/libs.versions.toml` : 依存バージョン管理

## License

TBD
