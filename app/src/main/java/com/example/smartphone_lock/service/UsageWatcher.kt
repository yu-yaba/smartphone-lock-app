package com.example.smartphone_lock.service

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/**
 * UsageStats を利用した前面アプリ監視のスタブ実装。
 * 後続ブランチで実際の監視ロジックを実装する。
 */
@Singleton
class UsageWatcher @Inject constructor(
    private val eventSource: ForegroundAppEventSource,
) : ForegroundAppMonitor {

    override fun start(scope: CoroutineScope, onMoveToForeground: (String) -> Unit) {
        // TODO: 後続ブランチで UsageStats をポーリングする実装に置き換える
    }
}
