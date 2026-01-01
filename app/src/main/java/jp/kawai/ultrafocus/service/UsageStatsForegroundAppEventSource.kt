package jp.kawai.ultrafocus.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UsageStatsManager を利用して MOVE_TO_FOREGROUND イベントを収集する実装。
 */
@Singleton
class UsageStatsForegroundAppEventSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : ForegroundAppEventSource {

    override fun collectRecentEvents(windowMillis: Long, onEvent: (String) -> Unit) {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usageStatsManager == null) {
            Log.w(TAG, "UsageStatsManager not available")
            return
        }
        val end = System.currentTimeMillis()
        val start = end - windowMillis
        try {
            val usageEvents = usageStatsManager.queryEvents(start, end) ?: return
            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && !event.packageName.isNullOrBlank()) {
                    onEvent(event.packageName)
                }
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to collect usage events", exception)
        }
    }

    companion object {
        private const val TAG = "UsageStatsSource"
    }
}
