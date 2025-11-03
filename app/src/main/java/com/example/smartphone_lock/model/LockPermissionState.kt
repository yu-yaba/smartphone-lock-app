package com.example.smartphone_lock.model

/**
 * ロック機能が必要とする各パーミッションの状態。
 * すべて true の場合にロックを開始できる想定。
 */
data class LockPermissionState(
    val overlayGranted: Boolean = false,
    val usageStatsGranted: Boolean = false,
    val notificationAccessGranted: Boolean = false
) {
    val allGranted: Boolean
        get() = overlayGranted && usageStatsGranted && notificationAccessGranted
}
