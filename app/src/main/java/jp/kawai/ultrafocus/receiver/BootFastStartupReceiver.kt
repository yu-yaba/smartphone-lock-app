package jp.kawai.ultrafocus.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import jp.kawai.ultrafocus.service.LockMonitorService
import jp.kawai.ultrafocus.service.OverlayLockService
import jp.kawai.ultrafocus.service.WatchdogScheduler
import jp.kawai.ultrafocus.service.WatchdogWorkScheduler

/**
 * Hilt 初期化を待たずに Direct Boot ストアだけを参照して、ロック中なら即座にサービスを起動する軽量レシーバ。
 * 端末ロック有無にかかわらず BOOT_COMPLETED 系のブロードキャストで動作する。
 *
 * ブート直後にブロードキャスト配送が遅延しても自力で再起動できるよう、
 * 5s / 30s / 90s で自己再実行のアラームを複数セットする。
 */
class BootFastStartupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in SUPPORTED_ACTIONS) return

        val pending = goAsync()
        val runner = {
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
                OverlayLockService.start(
                    appContext,
                    reason = "boot_fast_receiver",
                    bypassDebounce = true,
                    forceShow = true
                )
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
                    scheduleRetries(appContext)
                }
            } else {
                Log.i(TAG, "Fast boot start: no active lock state; skipping (retry=$retrying)")
            }
        }

        if (pending == null) {
            runner()
            return
        }

        CoroutineScope(Dispatchers.Default).launch {
            try {
                runner()
            } finally {
                pending.finish()
            }
        }
    }

    private fun scheduleRetries(context: Context) {
        RETRY_DELAYS_MILLIS.forEachIndexed { index, delay ->
            scheduleRetry(context, delay, RETRY_REQUEST_CODE_BASE + index)
        }
    }

    private fun scheduleRetry(context: Context, delayMillis: Long, requestCode: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, BootFastStartupReceiver::class.java).apply {
            action = ACTION_RETRY
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + delayMillis
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }.onFailure { throwable ->
            Log.w(TAG, "Exact alarm retry failed; fallback to inexact", throwable)
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }.onFailure { fallbackError ->
                Log.w(TAG, "Failed to schedule boot fast retry (inexact)", fallbackError)
            }
        }
    }

    internal companion object {
        private const val TAG = "BootFastStartupReceiver";
        private const val PREFS_NAME = "direct_boot_lock_state"
        private const val KEY_IS_LOCKED = "is_locked"
        private const val KEY_LOCK_END_TIMESTAMP = "lock_end_timestamp"
        internal const val RETRY_REQUEST_CODE_BASE = 9101
        // 互換性のため従来名も残す
        internal const val RETRY_REQUEST_CODE = RETRY_REQUEST_CODE_BASE
        private val RETRY_DELAYS_MILLIS = longArrayOf(5_000L, 30_000L, 90_000L)
        internal const val ACTION_RETRY = "jp.kawai.ultrafocus.action.BOOT_FAST_RETRY"

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
