package jp.kawai.ultrafocus.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import jp.kawai.ultrafocus.util.canUseExactAlarms

object ServiceRestartScheduler {

    private const val TAG = "ServiceRestartScheduler"
    private const val DEFAULT_DELAY_MILLIS = 1_000L
    private const val MIN_SCHEDULE_INTERVAL_MILLIS = 10_000L
    internal const val EXTRA_START_REASON = "extra_start_reason"
    private const val DEFAULT_RESTART_REASON = "service_restart"
    private val scheduleLock = Any()
    private val lastScheduleByRequest = mutableMapOf<Int, Long>()

    fun schedule(context: Context, serviceClass: Class<out Service>, requestCode: Int) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.w(TAG, "AlarmManager unavailable; cannot schedule restart for ${serviceClass.simpleName}")
            return
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        val shouldSchedule = synchronized(scheduleLock) {
            shouldScheduleLocked(requestCode, nowElapsed)
        }
        if (!shouldSchedule) {
            Log.w(
                TAG,
                "Skip restart schedule (debounced) for ${serviceClass.simpleName} request=$requestCode"
            )
            return
        }

        val intent = Intent(appContext, serviceClass).apply {
            putExtra(EXTRA_START_REASON, DEFAULT_RESTART_REASON)
        }
        val pendingIntent = PendingIntent.getService(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val triggerAt = SystemClock.elapsedRealtime() + DEFAULT_DELAY_MILLIS
        if (alarmManager.canUseExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } catch (security: SecurityException) {
                Log.w(TAG, "Exact alarm denied; fallback to inexact restart for ${serviceClass.simpleName}", security)
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
        } else {
            Log.w(TAG, "Exact alarm not allowed; schedule inexact restart for ${serviceClass.simpleName}")
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context, serviceClass: Class<out Service>, requestCode: Int) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = PendingIntent.getService(
            appContext,
            requestCode,
            Intent(appContext, serviceClass),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun immutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    private fun shouldScheduleLocked(requestCode: Int, nowElapsed: Long): Boolean {
        val hasLast = lastScheduleByRequest.containsKey(requestCode)
        val last = lastScheduleByRequest[requestCode] ?: 0L
        if (hasLast && nowElapsed - last < MIN_SCHEDULE_INTERVAL_MILLIS) {
            return false
        }
        lastScheduleByRequest[requestCode] = nowElapsed
        return true
    }

    internal fun shouldScheduleForTest(requestCode: Int, nowElapsed: Long): Boolean {
        return synchronized(scheduleLock) {
            shouldScheduleLocked(requestCode, nowElapsed)
        }
    }

    internal fun resetForTest() {
        synchronized(scheduleLock) {
            lastScheduleByRequest.clear()
        }
    }

    internal fun minScheduleIntervalForTest(): Long = MIN_SCHEDULE_INTERVAL_MILLIS
}
