package com.example.smartphone_lock.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.smartphone_lock.util.canUseExactAlarms

object ServiceRestartScheduler {

    private const val TAG = "ServiceRestartScheduler"
    private const val DEFAULT_DELAY_MILLIS = 1_000L
    internal const val EXTRA_START_REASON = "extra_start_reason"
    private const val DEFAULT_RESTART_REASON = "service_restart"

    fun schedule(context: Context, serviceClass: Class<out Service>, requestCode: Int) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.w(TAG, "AlarmManager unavailable; cannot schedule restart for ${serviceClass.simpleName}")
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
}
