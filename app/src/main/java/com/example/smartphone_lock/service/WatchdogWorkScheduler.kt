package com.example.smartphone_lock.service

import android.content.Context
import android.os.UserManager
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WatchdogWorkScheduler {

    private const val TAG = "WatchdogWorkScheduler"
    private const val UNIQUE_WORK_NAME = "watchdog_heartbeat_fallback"

    fun schedule(context: Context, delayMillis: Long = WatchdogScheduler.HEARTBEAT_INTERVAL_MILLIS) {
        val appContext = context.applicationContext
        if (!appContext.isUserUnlocked()) {
            Log.i(TAG, "Skip WorkManager heartbeat (user locked); cancel existing work if any")
            cancel(appContext)
            return
        }
        val request = buildRequest(delayMillis)
        Log.d(TAG, "Schedule WorkManager heartbeat delay=${delayMillis}ms id=${request.id}")
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        Log.d(TAG, "Cancel WorkManager heartbeat")
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun Context.isUserUnlocked(): Boolean =
        getSystemService(UserManager::class.java)?.isUserUnlocked ?: true

    private fun buildRequest(delayMillis: Long): OneTimeWorkRequest {
        val builder = OneTimeWorkRequestBuilder<WatchdogWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
        return builder.build()
    }
}
