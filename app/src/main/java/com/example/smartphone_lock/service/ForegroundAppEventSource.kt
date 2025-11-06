package com.example.smartphone_lock.service

/**
 * UsageStats などを通して前面アプリイベントを提供するデータソース。
 */
fun interface ForegroundAppEventSource {
    @Throws(SecurityException::class)
    fun collectRecentEvents(windowMillis: Long, onEvent: (String) -> Unit)
}
