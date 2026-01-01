package jp.kawai.ultrafocus.service

import kotlinx.coroutines.CoroutineScope

/**
 * 前面アプリの状態を監視し、フォアグラウンド遷移を通知するコンポーネント。
 */
fun interface ForegroundAppMonitor {
    fun start(scope: CoroutineScope, onMoveToForeground: (String) -> Unit)
}
