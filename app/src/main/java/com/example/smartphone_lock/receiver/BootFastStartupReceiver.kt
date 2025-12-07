package com.example.smartphone_lock.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.smartphone_lock.service.LockMonitorService
import com.example.smartphone_lock.service.OverlayLockService
import com.example.smartphone_lock.service.WatchdogScheduler
import com.example.smartphone_lock.service.WatchdogWorkScheduler
import android.os.UserManager

/**
 * Hilt 初期化を待たずに Direct Boot ストアだけを参照して、ロック中なら即座にサービスを起動する軽量レシーバ。
 * 端末ロック有無にかかわらず BOOT_COMPLETED 系のブロードキャストで動作する。
 */
class BootFastStartupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in SUPPORTED_ACTIONS) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val appContext = context.applicationContext
                val dpContext = appContext.createDeviceProtectedStorageContext()
                val prefs = dpContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val isLocked = prefs.getBoolean(KEY_IS_LOCKED, false)
                val hasEnd = prefs.contains(KEY_LOCK_END_TIMESTAMP)
                val lockEnd = if (hasEnd) prefs.getLong(KEY_LOCK_END_TIMESTAMP, 0L) else null
                val now = System.currentTimeMillis()

                val retrying = action == ACTION_RETRY

                if (isLocked && (lockEnd == null || lockEnd > now)) {
                    Log.i(TAG, "Fast boot start: locked state detected in DP store (retry=$retrying)")
                    LockMonitorService.start(appContext, reason = "boot_fast_receiver", bypassDebounce = true)
                    OverlayLockService.start(appContext, reason = "boot_fast_receiver", bypassDebounce = true)
                    // ウォッチドッグも即時復元（WorkManager は解錠後のみ）
                    WatchdogScheduler.scheduleHeartbeat(appContext, immediate = true)
                    WatchdogScheduler.scheduleLockExpiry(appContext, lockEnd)
                    if (appContext.isUserUnlocked()) {
                        WatchdogWorkScheduler.schedule(appContext, delayMillis = 0L)
                    } else {
                        Log.i(TAG, "Skip WorkManager schedule (user locked)")
                        WatchdogWorkScheduler.cancel(appContext)
                    }
                    // 念のため複数回再試行（初期化競合や描画拒否対策）
                    if (!retrying) {
                        scheduleRetry(appContext, RETRY_DELAY_MILLIS)
                        scheduleRetry(appContext, RETRY_DELAY_LONG_MILLIS)
                    }
                } else {
                    Log.i(TAG, "Fast boot start: no active lock state; skipping (retry=$retrying)")
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun scheduleRetry(context: Context, delayMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, BootFastStartupReceiver::class.java).apply {
            action = ACTION_RETRY
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            RETRY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + delayMillis
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }.onFailure { Log.w(TAG, "Failed to schedule boot fast retry", it) }
    }

    internal companion object {
        private const val TAG = "BootFastStartup";
        private const val PREFS_NAME = "direct_boot_lock_state"
        private const val KEY_IS_LOCKED = "is_locked"
        private const val KEY_LOCK_END_TIMESTAMP = "lock_end_timestamp"
        internal const val RETRY_REQUEST_CODE = 9101
        internal const val RETRY_DELAY_MILLIS = 4_000L
        internal const val RETRY_DELAY_LONG_MILLIS = 12_000L
        internal const val ACTION_RETRY = "com.example.smartphone_lock.action.BOOT_FAST_RETRY"

        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_RETRY
        )
    }
}

private fun Context.isUserUnlocked(): Boolean =
    getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
