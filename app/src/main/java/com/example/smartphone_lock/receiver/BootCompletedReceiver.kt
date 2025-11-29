package com.example.smartphone_lock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import com.example.smartphone_lock.data.datastore.DeviceProtectedLockStatePreferences
import com.example.smartphone_lock.data.datastore.DataStoreManager
import com.example.smartphone_lock.data.datastore.LockStatePreferences
import com.example.smartphone_lock.service.LockMonitorService
import com.example.smartphone_lock.service.OverlayLockService
import com.example.smartphone_lock.service.WatchdogScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in SUPPORTED_ACTIONS) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (action == Intent.ACTION_USER_UNLOCKED) {
                    dataStoreManager.syncCredentialStoreFromDeviceProtected()
                }

                val now = System.currentTimeMillis()
                val storageTier = resolveStorageTier(appContext, action)
                val ceSnapshot = runCatching { dataStoreManager.lockState.first().toSnapshot() }
                    .onFailure {
                        Log.w(TAG, "CE snapshot unavailable after $action; will fallback to DP", it)
                    }
                    .getOrNull()
                val dpSnapshot = dataStoreManager.deviceProtectedSnapshot().toSnapshot()

                val preferred = when (storageTier) {
                    StorageTier.CREDENTIAL_ENCRYPTED -> ceSnapshot ?: dpSnapshot
                    StorageTier.DEVICE_PROTECTED -> dpSnapshot
                }

                val lockSnapshot = when {
                    preferred.isActive(now) -> preferred
                    dpSnapshot.isActive(now) -> {
                        // CE が空だが DP にロックが残っている場合は復元を試みる
                        if (storageTier == StorageTier.CREDENTIAL_ENCRYPTED) {
                            dataStoreManager.syncCredentialStoreFromDeviceProtected()
                        }
                        dpSnapshot
                    }
                    else -> preferred
                }

                val lockActive = lockSnapshot.isActive(now)

                if (!lockActive) {
                    Log.i(TAG, "No active lock state after $action; skipping service restart")
                    WatchdogScheduler.cancelHeartbeat(appContext)
                    WatchdogScheduler.cancelLockExpiry(appContext)
                } else {
                    Log.i(
                        TAG,
                        "Lock active after $action (tier=$storageTier); restarting services"
                    )
                    restartLockServices(appContext)
                    WatchdogScheduler.scheduleHeartbeat(appContext)
                    WatchdogScheduler.scheduleLockExpiry(appContext, lockSnapshot.lockEndTimestamp)
                }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to restore lock state after boot", throwable)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun restartLockServices(context: Context) {
        LockMonitorService.start(context)
        OverlayLockService.start(context)
    }

    private fun resolveStorageTier(context: Context, action: String): StorageTier {
        return when (action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> StorageTier.DEVICE_PROTECTED
            Intent.ACTION_USER_UNLOCKED -> StorageTier.CREDENTIAL_ENCRYPTED
            Intent.ACTION_MY_PACKAGE_REPLACED -> if (context.isUserUnlocked()) {
                StorageTier.CREDENTIAL_ENCRYPTED
            } else {
                StorageTier.DEVICE_PROTECTED
            }
            Intent.ACTION_BOOT_COMPLETED -> if (context.isUserUnlocked()) {
                StorageTier.CREDENTIAL_ENCRYPTED
            } else {
                StorageTier.DEVICE_PROTECTED
            }
            else -> StorageTier.CREDENTIAL_ENCRYPTED
        }
    }

    private companion object {
        private const val TAG = "BootCompletedReceiver"

        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )

        private data class LockSnapshot(
            val isLocked: Boolean,
            val lockEndTimestamp: Long?
        )

        private enum class StorageTier {
            DEVICE_PROTECTED,
            CREDENTIAL_ENCRYPTED
        }
    }

    private fun Context.isUserUnlocked(): Boolean =
        getSystemService(UserManager::class.java)?.isUserUnlocked ?: true

    private fun LockStatePreferences.toSnapshot(): LockSnapshot = LockSnapshot(
        isLocked = isLocked,
        lockEndTimestamp = lockEndTimestamp
    )

    private fun DeviceProtectedLockStatePreferences.toSnapshot(): LockSnapshot = LockSnapshot(
        isLocked = isLocked,
        lockEndTimestamp = lockEndTimestamp
    )

    private fun LockSnapshot.isActive(now: Long): Boolean =
        isLocked && (lockEndTimestamp == null || lockEndTimestamp > now)
}
