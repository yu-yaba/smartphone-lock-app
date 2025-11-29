package com.example.smartphone_lock.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object WatchdogScheduler {

    private const val TAG = "WatchdogScheduler"
    private const val HEARTBEAT_INTERVAL_MILLIS = 3 * 60 * 1000L
    private const val HEARTBEAT_REQUEST_CODE = 8001
    private const val LOCK_EXPIRY_REQUEST_CODE = 8002

    fun scheduleHeartbeat(context: Context) {
        scheduleExact(
            context.applicationContext,
            WatchdogReceiver.ACTION_HEARTBEAT,
            HEARTBEAT_REQUEST_CODE,
            System.currentTimeMillis() + HEARTBEAT_INTERVAL_MILLIS
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
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, WatchdogReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun cancel(context: Context, action: String, requestCode: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, WatchdogReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun immutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }
}
