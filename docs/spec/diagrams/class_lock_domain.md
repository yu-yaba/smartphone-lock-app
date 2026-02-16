# クラス図
最終更新日: 2026-02-14

更新理由:

## この図で示すこと

- ロック機能の全体像（UI起点、状態保存、監視サービス、再実行）をわかりやすく示す。
- Interfaceと実装がどうつながっているか（DIで差し替え可能な境界）を示す。
- ロック開始時に、どのクラスがどの順で連動するかを示す

## クラス図

配色ルール:
- 青: 入口の層。ユーザー操作を受けて、ロック開始の流れを起動する。
- 緑: 判定と保存の層。権限チェックやロック状態の保存先をまとめる。
- 赤: 実行中の処理を担当する層。監視やオーバーレイ表示を動かす。
- 灰: 裏方の層。監視のインターフェース境界と、再実行を補完するウォッチドッグを表す。

矢印ルール:
- `-->` は「使う」依存を表す。クラス内部の通常処理で、別クラスを道具として参照・利用する関係。
- `..>` は「動かす」依存を表す。`start` / `stop` / `schedule` のように、外部コンポーネントを起動・制御する関係。


```mermaid
classDiagram
    direction LR

    class LockScreenViewModel

    class LockPermissionsRepository {
      <<interface>>
    }
    class DefaultLockPermissionsRepository

    class LockRepository {
      <<interface>>
    }
    class DefaultLockRepository

    class DataStoreManager
    class DirectBootLockStateStore

    class LockMonitorService
    class OverlayLockService
    class OverlayManager
    class LockUiLauncher

    class ForegroundAppMonitor {
      <<interface>>
    }
    class UsageWatcher

    class ForegroundAppEventSource {
      <<interface>>
    }
    class UsageStatsForegroundAppEventSource

    class WatchdogScheduler {
      <<object>>
    }
    class WatchdogWorkScheduler {
      <<object>>
    }

    LockPermissionsRepository <|.. DefaultLockPermissionsRepository
    LockRepository <|.. DefaultLockRepository
    ForegroundAppMonitor <|.. UsageWatcher
    ForegroundAppEventSource <|.. UsageStatsForegroundAppEventSource

    DefaultLockRepository --> DataStoreManager
    DataStoreManager *-- DirectBootLockStateStore

    LockScreenViewModel --> LockPermissionsRepository
    LockScreenViewModel --> LockRepository
    LockScreenViewModel --> DataStoreManager : [1] ロック状態を保存
    LockScreenViewModel ..> LockMonitorService : [2] 監視を開始
    LockScreenViewModel ..> OverlayLockService : [3] オーバーレイを開始
    LockScreenViewModel ..> WatchdogScheduler : [4] Alarm監視を設定
    LockScreenViewModel ..> WatchdogWorkScheduler : [5] WorkManager補完を設定

    LockMonitorService --> ForegroundAppMonitor
    LockMonitorService --> LockRepository
    LockMonitorService --> OverlayManager
    LockMonitorService --> LockUiLauncher

    OverlayManager ..> LockMonitorService
    OverlayManager ..> OverlayLockService

    OverlayLockService --> DataStoreManager
    OverlayLockService --> LockRepository
    OverlayLockService --> ForegroundAppEventSource

    %% fallback styling (widely supported)
    style LockScreenViewModel fill:#E8F0FE,stroke:#1A73E8,stroke-width:2px

    style LockPermissionsRepository fill:#E8F5E9,stroke:#2E7D32,stroke-width:2px
    style DefaultLockPermissionsRepository fill:#E8F5E9,stroke:#2E7D32,stroke-width:2px

    style LockRepository fill:#E8F5E9,stroke:#2E7D32,stroke-width:2px
    style DefaultLockRepository fill:#E8F5E9,stroke:#2E7D32,stroke-width:2px

    style DataStoreManager fill:#E8F5E9,stroke:#2E7D32,stroke-width:2px
    style DirectBootLockStateStore fill:#E8F5E9,stroke:#2E7D32,stroke-width:2px

    style LockMonitorService fill:#FFEBEE,stroke:#C62828,stroke-width:2px
    style OverlayLockService fill:#FFEBEE,stroke:#C62828,stroke-width:2px
    style OverlayManager fill:#FFEBEE,stroke:#C62828,stroke-width:2px
    style LockUiLauncher fill:#FFEBEE,stroke:#C62828,stroke-width:2px

    style ForegroundAppMonitor fill:#ECEFF1,stroke:#455A64,stroke-width:2px
    style UsageWatcher fill:#ECEFF1,stroke:#455A64,stroke-width:2px
    style ForegroundAppEventSource fill:#ECEFF1,stroke:#455A64,stroke-width:2px
    style UsageStatsForegroundAppEventSource fill:#ECEFF1,stroke:#455A64,stroke-width:2px

    style WatchdogScheduler fill:#ECEFF1,stroke:#455A64,stroke-width:2px
    style WatchdogWorkScheduler fill:#ECEFF1,stroke:#455A64,stroke-width:2px
```

主要経路ラベル:
- `[1]` ロック状態を保存する。
- `[2]` 前面アプリ監視サービスを起動する。
- `[3]` オーバーレイ表示サービスを起動する。
- `[4]` Alarm ベースの監視再実行を設定する。
- `[5]` WorkManager ベースの監視再実行を設定する。

## 図で意図的に省略した内容

- ブート復元まわりの細かい分岐（BootCompletedReceiver/ BootFastStartupReceiver / WatchdogReceiver）
    - この図の目的は「ロック開始時の主要クラス連携」を伝えることであり、復元フローまで入れると焦点がぼやけるため。
- 例外処理、デバウンス、補助ストア（AllowedAppLaunchStoreなど）の詳細。
    - これらは挙動の安定化に関する補助要素であり、主要な責務分担を理解する初見読者には情報量が多すぎるため。
- UIコンポーザブルやオーバーレイ内部ビューの描画詳細
    - この図はクラス間の役割と依存関係を示すための図であり、画面描画の詳細は別の図や実装コードで追う方が分かりやすいため。
