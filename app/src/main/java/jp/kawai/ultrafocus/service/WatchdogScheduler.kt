package jp.kawai.ultrafocus.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import jp.kawai.ultrafocus.receiver.WatchdogReceiver
import jp.kawai.ultrafocus.util.canUseExactAlarms

object WatchdogScheduler {

    private const val TAG = "WatchdogScheduler"
    const val HEARTBEAT_INTERVAL_MILLIS = 60_000L
    private const val HEARTBEAT_REQUEST_CODE = 8001
    private const val LOCK_EXPIRY_REQUEST_CODE = 8002

    fun scheduleHeartbeat(context: Context, immediate: Boolean = false) {
        val triggerAt = System.currentTimeMillis() + if (immediate) 0L else HEARTBEAT_INTERVAL_MILLIS
        Log.d(
            TAG,
            "Schedule heartbeat action delay=${triggerAt - System.currentTimeMillis()}ms immediate=$immediate"
        )
        scheduleExact(
            context.applicationContext,
            WatchdogReceiver.ACTION_HEARTBEAT,
            HEARTBEAT_REQUEST_CODE,
            triggerAt
        )
    }

    fun cancelHeartbeat(context: Context) {
        cancel(context.applicationContext, WatchdogReceiver.ACTION_HEARTBEAT, HEARTBEAT_REQUEST_CODE)
    }

    fun scheduleLockExpiry(context: Context, lockEndTimestamp: Long?) {
        if (lockEndTimestamp == null) {
            cancelLockExpiry(context)
            return
        }
        scheduleExact(
            context.applicationContext,
            WatchdogReceiver.ACTION_LOCK_EXPIRY,
            LOCK_EXPIRY_REQUEST_CODE,
            lockEndTimestamp
        )
    }

    fun cancelLockExpiry(context: Context) {
        cancel(context.applicationContext, WatchdogReceiver.ACTION_LOCK_EXPIRY, LOCK_EXPIRY_REQUEST_CODE)
    }

    private fun scheduleExact(
        context: Context,
        action: String,
        requestCode: Int,
        triggerAtMillis: Long
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (alarmManager == null) {
            Log.w(TAG, "AlarmManager unavailable; cannot schedule $action")
            return
        }
        if (!alarmManager.canUseExactAlarms()) {
            Log.w(TAG, "Exact alarm not allowed; schedule inexact for $action triggerAt=$triggerAtMillis")
            scheduleInexact(alarmManager, triggerAtMillis, pendingIntent(context, action, requestCode))
            return
        }
        val pendingIntent = pendingIntent(context, action, requestCode)
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } catch (security: SecurityException) {
            Log.w(TAG, "Exact alarm denied; fallback to inexact for $action", security)
            scheduleInexact(alarmManager, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancel(context: Context, action: String, requestCode: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntent(context, action, requestCode)
        alarmManager.cancel(pendingIntent)
    }

    private fun immutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, WatchdogReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    private fun scheduleInexact(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }
}
