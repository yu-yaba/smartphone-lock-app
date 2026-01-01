package jp.kawai.ultrafocus.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import jp.kawai.ultrafocus.data.datastore.DataStoreManager
import jp.kawai.ultrafocus.service.LockMonitorService
import jp.kawai.ultrafocus.service.OverlayLockService
import jp.kawai.ultrafocus.service.WatchdogScheduler
import jp.kawai.ultrafocus.service.WatchdogWorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-only receiver for automated reboot scenarios.
 * Actions:
 * - jp.kawai.ultrafocus.action.TEST_LOCK   (requires extras: durationMinutes Long optional, endTimestampMillis Long optional)
 * - jp.kawai.ultrafocus.action.TEST_UNLOCK
 * - jp.kawai.ultrafocus.action.TEST_STATUS (logs current DP snapshot)
 */
@AndroidEntryPoint
class TestControlReceiver : BroadcastReceiver() {

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val appContext = context.applicationContext
        when (action) {
            ACTION_TEST_LOCK -> handleLock(appContext, intent)
            ACTION_TEST_UNLOCK -> handleUnlock(appContext)
            ACTION_TEST_STATUS -> logStatus(appContext)
        }
    }

    private fun handleLock(context: Context, intent: Intent) {
        val durationMinutes = intent.getLongExtra(EXTRA_DURATION_MINUTES, DEFAULT_MINUTES)
        val now = System.currentTimeMillis()
        val endAt = intent.getLongExtra(EXTRA_END_TIMESTAMP, now + durationMinutes * 60_000)
        CoroutineScope(Dispatchers.Default).launch {
            dataStoreManager.updateLockState(
                isLocked = true,
                lockStartTimestamp = now,
                lockEndTimestamp = endAt
            )
        }
        LockMonitorService.start(context, bypassDebounce = true, reason = "test_lock")
        OverlayLockService.start(context, bypassDebounce = true, reason = "test_lock")
        WatchdogScheduler.scheduleHeartbeat(context, immediate = true)
        WatchdogScheduler.scheduleLockExpiry(context, endAt)
        if (context.isUserUnlocked()) {
            WatchdogWorkScheduler.schedule(context, delayMillis = 0L)
        }
        Log.i(TAG, "Test lock scheduled until=$endAt minutes=$durationMinutes")
    }

    private fun handleUnlock(context: Context) {
        CoroutineScope(Dispatchers.Default).launch {
            dataStoreManager.updateLockState(false, null, null)
        }
        WatchdogScheduler.cancelHeartbeat(context)
        WatchdogScheduler.cancelLockExpiry(context)
        WatchdogWorkScheduler.cancel(context)
        LockMonitorService.stop(context)
        OverlayLockService.stop(context)
        Log.i(TAG, "Test unlock executed")
    }

    private fun logStatus(context: Context) {
        val snapshot = dataStoreManager.deviceProtectedSnapshot()
        Log.i(TAG, "Status DP snapshot isLocked=${snapshot.isLocked} end=${snapshot.lockEndTimestamp}")
    }

    companion object {
        const val ACTION_TEST_LOCK = "jp.kawai.ultrafocus.action.TEST_LOCK"
        const val ACTION_TEST_UNLOCK = "jp.kawai.ultrafocus.action.TEST_UNLOCK"
        const val ACTION_TEST_STATUS = "jp.kawai.ultrafocus.action.TEST_STATUS"
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
        const val EXTRA_END_TIMESTAMP = "extra_end_timestamp"
        private const val DEFAULT_MINUTES = 10L
        private const val TAG = "TestControlReceiver"
    }
}

private fun Context.isUserUnlocked(): Boolean =
    getSystemService(android.os.UserManager::class.java)?.isUserUnlocked ?: true
