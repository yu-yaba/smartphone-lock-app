package com.example.smartphone_lock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.smartphone_lock.data.datastore.DataStoreManager
import com.example.smartphone_lock.data.datastore.DeviceProtectedLockStatePreferences
import com.example.smartphone_lock.data.datastore.LockStatePreferences
import com.example.smartphone_lock.service.LockMonitorService
import com.example.smartphone_lock.service.OverlayLockService
import com.example.smartphone_lock.service.WatchdogScheduler
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
            return
        }
        LockMonitorService.start(context)
        OverlayLockService.start(context)
        WatchdogScheduler.scheduleHeartbeat(context)
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
        LockMonitorService.stop(context)
        OverlayLockService.stop(context)
    }

    private fun currentSnapshot(): LockStatePreferences {
        val dpState = dataStoreManager.deviceProtectedSnapshot()
        return dpState.toSnapshot()
    }

    companion object {
        const val ACTION_HEARTBEAT = "com.example.smartphone_lock.action.HEARTBEAT"
        const val ACTION_LOCK_EXPIRY = "com.example.smartphone_lock.action.LOCK_EXPIRY"
        private const val TAG = "WatchdogReceiver"
    }
}

private fun DeviceProtectedLockStatePreferences.toSnapshot(): LockStatePreferences = LockStatePreferences(
    isLocked = isLocked,
    lockStartTimestamp = lockStartTimestamp,
    lockEndTimestamp = lockEndTimestamp
)
