package jp.kawai.ultrafocus.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import jp.kawai.ultrafocus.data.datastore.DeviceProtectedLockStatePreferences
import jp.kawai.ultrafocus.data.datastore.DataStoreManager
import jp.kawai.ultrafocus.data.datastore.LockStatePreferences
import jp.kawai.ultrafocus.service.LockMonitorService
import jp.kawai.ultrafocus.service.OverlayLockService
import jp.kawai.ultrafocus.service.WatchdogScheduler
import jp.kawai.ultrafocus.service.WatchdogWorkScheduler
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

        // Fast path: Direct Boot 領域のスナップショットを即座に確認し、アクティブなら迷わず起動する
        val now = System.currentTimeMillis()
        runCatching { dataStoreManager.deviceProtectedSnapshot() }
            .onSuccess { snapshot ->
                if (snapshot.isLocked && (snapshot.lockEndTimestamp == null || snapshot.lockEndTimestamp > now)) {
                    Log.i(TAG, "Fast start lock services from DP snapshot after $action")
                    restartLockServices(context, reason = "boot_fastpath")
                }
            }
            .onFailure { Log.w(TAG, "DP snapshot unavailable for fast start after $action", it) }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (action == Intent.ACTION_USER_UNLOCKED) {
                    dataStoreManager.syncCredentialStoreFromDeviceProtected()
                }

                if (!appContext.isUserUnlocked()) {
                    // Direct Boot 中は残存 WorkManager を止め、Alarm のみに依存する
                    WatchdogWorkScheduler.cancel(appContext)
                }

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
                    WatchdogWorkScheduler.cancel(appContext)
                } else {
                    Log.i(
                        TAG,
                        "Lock active after $action (tier=$storageTier); restarting services"
                    )
                    restartLockServices(appContext, reason = "boot_restore")
                    WatchdogScheduler.scheduleHeartbeat(appContext, immediate = true)
                    WatchdogScheduler.scheduleLockExpiry(appContext, lockSnapshot.lockEndTimestamp)
                    if (appContext.isUserUnlocked()) {
                        WatchdogWorkScheduler.schedule(appContext, delayMillis = 0L)
                    } else {
                        Log.i(TAG, "Skip WorkManager schedule (user locked)")
                        WatchdogWorkScheduler.cancel(appContext)
                    }
                    // 念のため再試行をセット（初期化競合対策）。Fastレシーバが登録されていても保険として残す。
                    scheduleRetry(appContext)
                }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to restore lock state after boot", throwable)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun scheduleRetry(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, BootFastStartupReceiver::class.java).apply {
            action = BootFastStartupReceiver.ACTION_RETRY
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            BootFastStartupReceiver.RETRY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + RETRY_DELAY_MILLIS
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }.onFailure { throwable ->
            Log.w(TAG, "Exact alarm retry failed; fallback to inexact", throwable)
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }.onFailure { fallbackError ->
                Log.w(TAG, "Failed to schedule boot retry (inexact)", fallbackError)
            }
        }
    }

    private fun restartLockServices(context: Context, reason: String) {
        // ブート直後はデバウンス不要。確実に立ち上げる。
        LockMonitorService.start(context, reason = reason, bypassDebounce = true)
        OverlayLockService.start(context, reason = reason, bypassDebounce = true, forceShow = true)
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
        internal const val RETRY_DELAY_MILLIS = 4_000L

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
