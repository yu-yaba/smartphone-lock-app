package jp.kawai.ultrafocus.service

import android.content.Context
import android.os.UserManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import jp.kawai.ultrafocus.data.datastore.DirectBootLockStateStore

class WatchdogWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val userUnlocked = applicationContext.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
        if (!userUnlocked) {
            Log.i(TAG, "User locked; skip WorkManager fallback and rely on Alarm watchdog")
            WatchdogScheduler.scheduleHeartbeat(applicationContext)
            WatchdogWorkScheduler.cancel(applicationContext)
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val lockSnapshot = DirectBootLockStateStore(applicationContext).snapshot()
        val lockActive = lockSnapshot.isLocked && (lockSnapshot.lockEndTimestamp == null || lockSnapshot.lockEndTimestamp > now)
        Log.d(
            TAG,
            "WorkManager heartbeat lockActive=$lockActive lockEnd=${lockSnapshot.lockEndTimestamp} now=$now id=$id"
        )
        if (lockActive) {
            LockMonitorService.start(applicationContext, reason = "workmanager", bypassDebounce = true)
            OverlayLockService.start(applicationContext, reason = "workmanager", bypassDebounce = true)
            WatchdogScheduler.scheduleHeartbeat(applicationContext)
            WatchdogScheduler.scheduleLockExpiry(applicationContext, lockSnapshot.lockEndTimestamp)
            WatchdogWorkScheduler.schedule(applicationContext)
        } else {
            WatchdogScheduler.cancelHeartbeat(applicationContext)
            WatchdogScheduler.cancelLockExpiry(applicationContext)
            WatchdogWorkScheduler.cancel(applicationContext)
            return Result.success()
        }
        return Result.success()
    }

    private companion object {
        private const val TAG = "WatchdogWorker"
    }
}
