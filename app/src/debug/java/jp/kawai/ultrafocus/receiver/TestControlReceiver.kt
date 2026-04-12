package jp.kawai.ultrafocus.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import jp.kawai.ultrafocus.data.datastore.DataStoreManager
import jp.kawai.ultrafocus.service.AllowedAppLaunchStore
import jp.kawai.ultrafocus.service.LockMonitorService
import jp.kawai.ultrafocus.service.OverlayLockService
import jp.kawai.ultrafocus.service.WatchdogScheduler
import jp.kawai.ultrafocus.service.WatchdogWorkScheduler
import jp.kawai.ultrafocus.util.AllowedAppResolver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

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
        Log.i(TAG, "Received action=$action")
        when (action) {
            ACTION_TEST_LOCK -> handleLock(appContext, intent)
            ACTION_TEST_UNLOCK -> handleUnlock(appContext)
            ACTION_TEST_STATUS -> logStatus(appContext)
            ACTION_TEST_PREPARE_ALLOWED_DIALER -> handlePrepareAllowedDialer(appContext)
        }
    }

    private fun handleLock(context: Context, intent: Intent) {
        val durationMinutes = intent.getLongExtra(EXTRA_DURATION_MINUTES, DEFAULT_MINUTES)
        val now = System.currentTimeMillis()
        val endAt = intent.getLongExtra(EXTRA_END_TIMESTAMP, now + durationMinutes * 60_000)
        runBlocking {
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
        runBlocking {
            dataStoreManager.updateLockState(false, null, null)
        }
        AllowedAppLaunchStore.clear(context)
        WatchdogScheduler.cancelHeartbeat(context)
        WatchdogScheduler.cancelLockExpiry(context)
        WatchdogWorkScheduler.cancel(context)
        LockMonitorService.stop(context)
        OverlayLockService.stop(context)
        Log.i(TAG, "Test unlock executed")
    }

    private fun handlePrepareAllowedDialer(context: Context) {
        val targetPackage = AllowedAppResolver.resolveDialerPackage(context)
        if (targetPackage.isNullOrBlank()) {
            AllowedAppLaunchStore.clear(context)
            LockMonitorService.syncAllowedAppMonitorMode(context)
            OverlayLockService.setAllowedAppSuppressed(
                context,
                suppressed = false,
                reason = "test_prepare_allowed_dialer_failed"
            )
            Log.w(TAG, "No default dialer resolved for test preparation")
            return
        }
        OverlayLockService.setAllowedAppSuppressed(
            context,
            suppressed = true,
            reason = "test_prepare_allowed_dialer"
        )
        AllowedAppLaunchStore.startLaunch(context)
        AllowedAppLaunchStore.startSession(context)
        AllowedAppLaunchStore.setAllowed(context, targetPackage)
        LockMonitorService.syncAllowedAppMonitorMode(context)
        Log.i(TAG, "Prepared allowed dialer package=$targetPackage")
    }

    private fun logStatus(context: Context) {
        val snapshot = dataStoreManager.deviceProtectedSnapshot()
        Log.i(TAG, "Status DP snapshot isLocked=${snapshot.isLocked} end=${snapshot.lockEndTimestamp}")
    }

    companion object {
        const val ACTION_TEST_LOCK = "jp.kawai.ultrafocus.action.TEST_LOCK"
        const val ACTION_TEST_UNLOCK = "jp.kawai.ultrafocus.action.TEST_UNLOCK"
        const val ACTION_TEST_STATUS = "jp.kawai.ultrafocus.action.TEST_STATUS"
        const val ACTION_TEST_PREPARE_ALLOWED_DIALER =
            "jp.kawai.ultrafocus.action.TEST_PREPARE_ALLOWED_DIALER"
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
        const val EXTRA_END_TIMESTAMP = "extra_end_timestamp"
        private const val DEFAULT_MINUTES = 10L
        private const val TAG = "TestControlReceiver"
    }
}

private fun Context.isUserUnlocked(): Boolean =
    getSystemService(android.os.UserManager::class.java)?.isUserUnlocked ?: true
