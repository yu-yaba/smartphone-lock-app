package jp.kawai.ultrafocus.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import jp.kawai.ultrafocus.data.datastore.DataStoreManager
import jp.kawai.ultrafocus.data.datastore.DeviceProtectedLockStatePreferences
import jp.kawai.ultrafocus.data.datastore.LockStatePreferences
import jp.kawai.ultrafocus.service.LockMonitorService
import jp.kawai.ultrafocus.service.OverlayLockService
import jp.kawai.ultrafocus.service.WatchdogScheduler
import jp.kawai.ultrafocus.service.WatchdogWorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class WatchdogReceiver : BroadcastReceiver() {

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in setOf(ACTION_HEARTBEAT, ACTION_LOCK_EXPIRY)) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (action) {
                    ACTION_HEARTBEAT -> handleHeartbeat(appContext)
                    ACTION_LOCK_EXPIRY -> handleLockExpiry(appContext)
                }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Watchdog handling failed for $action", throwable)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleHeartbeat(context: Context) {
        val snapshot = currentSnapshot()
        val lockActive = snapshot.isLocked && snapshot.lockEndTimestamp?.let { it > System.currentTimeMillis() } != false
        if (!lockActive) {
            WatchdogScheduler.cancelHeartbeat(context)
            WatchdogWorkScheduler.cancel(context)
            return
        }
        LockMonitorService.start(context)
        OverlayLockService.start(context)
        WatchdogScheduler.scheduleHeartbeat(context)
        if (context.isUserUnlocked()) {
            WatchdogWorkScheduler.schedule(context)
        } else {
            Log.d(TAG, "Skip WorkManager heartbeat (user locked)")
        }
        if (snapshot.lockEndTimestamp != null) {
            WatchdogScheduler.scheduleLockExpiry(context, snapshot.lockEndTimestamp)
        }
    }

    private suspend fun handleLockExpiry(context: Context) {
        val snapshot = currentSnapshot()
        if (snapshot.isLocked) {
            withContext(Dispatchers.Default) {
                dataStoreManager.updateLockState(false, null, null)
            }
        }
        WatchdogScheduler.cancelHeartbeat(context)
        WatchdogScheduler.cancelLockExpiry(context)
        WatchdogWorkScheduler.cancel(context)
        LockMonitorService.stop(context)
        OverlayLockService.stop(context)
    }

    private fun currentSnapshot(): LockStatePreferences {
        val dpState = dataStoreManager.deviceProtectedSnapshot()
        return dpState.toSnapshot()
    }

    companion object {
        const val ACTION_HEARTBEAT = "jp.kawai.ultrafocus.action.HEARTBEAT"
        const val ACTION_LOCK_EXPIRY = "jp.kawai.ultrafocus.action.LOCK_EXPIRY"
        private const val TAG = "WatchdogReceiver"
    }
}

private fun DeviceProtectedLockStatePreferences.toSnapshot(): LockStatePreferences = LockStatePreferences(
    isLocked = isLocked,
    lockStartTimestamp = lockStartTimestamp,
    lockEndTimestamp = lockEndTimestamp
)

private fun Context.isUserUnlocked(): Boolean =
    getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
