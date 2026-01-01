package jp.kawai.ultrafocus.model

/**
 * ロックに必須のパーミッション状態。通知リスナーは現時点で利用しないため除外。
 */
data class LockPermissionState(
    val overlayGranted: Boolean = false,
    val usageStatsGranted: Boolean = false,
    val exactAlarmGranted: Boolean = false
) {
    val allGranted: Boolean
        get() = overlayGranted && usageStatsGranted && exactAlarmGranted
}
