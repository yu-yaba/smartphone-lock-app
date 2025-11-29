package com.example.smartphone_lock.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.UserManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.smartphone_lock.R
import com.example.smartphone_lock.data.repository.LockRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ForegroundService として常駐し UsageStats 監視を行うサービス。
 */
@AndroidEntryPoint
class LockMonitorService : Service() {

    @Inject
    lateinit var foregroundAppMonitor: ForegroundAppMonitor

    @Inject
    lateinit var overlayManager: OverlayManager

    @Inject
    lateinit var lockRepository: LockRepository

    @Inject
    lateinit var lockUiLauncher: LockUiLauncher

    private var serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var lockStateJob: Job? = null
    private var deviceProtectedLockStateJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile
    private var isLocked: Boolean = false
    private var foregroundStarted = false
    private val overlayThrottler = PackageEventThrottler(BLACKLIST_OVERLAY_DEBOUNCE_MILLIS)
    private val lockUiRedirectThrottler = PackageEventThrottler(LOCK_UI_REDIRECT_DEBOUNCE_MILLIS)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()
        startMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        releaseWakeLock()
        lockStateJob = null
        deviceProtectedLockStateJob = null
        foregroundStarted = false
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        ServiceRestartScheduler.schedule(this, LockMonitorService::class.java, RESTART_REQUEST_CODE)
    }

    private fun startMonitoring() {
        ensureCredentialEncryptedMonitoring()
        startDeviceProtectedMonitoringIfNeeded()
        foregroundAppMonitor.start(serviceScope) { packageName ->
            if (!isLocked) return@start
            val normalized = packageName.trim()
            if (normalized.isEmpty()) return@start
            if (lockRepository.shouldForceLockUi(normalized)) {
                val reason = resolveReasonLabel(normalized)
                serviceScope.launch { handleForcedRedirect(normalized, reason) }
            }
        }
    }

    private fun ensureCredentialEncryptedMonitoring() {
        if (lockStateJob?.isActive == true) return
        lockStateJob = serviceScope.launch {
            lockRepository.lockState.collectLatest { state ->
                updateCurrentLockState(state.isLocked)
            }
        }
    }

    private fun startDeviceProtectedMonitoringIfNeeded() {
        if (!supportsDeviceProtectedStorage()) return
        if (isUserUnlocked()) {
            deviceProtectedLockStateJob?.cancel()
            deviceProtectedLockStateJob = null
            return
        }
        if (deviceProtectedLockStateJob?.isActive == true) return
        deviceProtectedLockStateJob = serviceScope.launch {
            lockRepository.deviceProtectedLockState.collectLatest { state ->
                if (isUserUnlocked()) {
                    cancel("User unlocked; DP monitoring no longer needed")
                } else {
                    updateCurrentLockState(state.isLocked)
                }
            }
        }
    }

    private fun updateCurrentLockState(nextState: Boolean) {
        isLocked = nextState
        if (nextState) {
            overlayManager.show()
        }
    }

    private suspend fun handleBlacklistedPackage(packageName: String, reason: String) {
        val shouldForceOverlay = overlayThrottler.shouldTrigger(packageName)
        if (shouldForceOverlay) {
            Log.d(TAG, "Force overlay for package=$packageName reason=$reason")
            overlayManager.show()
        } else {
            Log.v(TAG, "Skip overlay (debounced) for package=$packageName reason=$reason")
        }
    }

    private suspend fun handleForcedRedirect(packageName: String, reason: String) {
        handleBlacklistedPackage(packageName, reason)
        val shouldLaunch = lockUiRedirectThrottler.shouldTrigger(packageName)
        if (shouldLaunch) {
            Log.d(TAG, "Launching lock UI for package=$packageName reason=$reason")
            lockUiLauncher.bringToFront()
        } else {
            Log.v(TAG, "Skip lock UI launch (debounced) for package=$packageName reason=$reason")
        }
    }

    private fun resolveReasonLabel(packageName: String): String {
        val normalized = packageName.trim()
        if (normalized.contains("permissioncontroller")) {
            return "permission_controller"
        }
        return "settings"
    }

    private fun ensureForeground() {
        if (foregroundStarted) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotificationPermissionCompat()) {
            Log.w(TAG, "Notification permission missing; running monitor without foreground")
            foregroundStarted = true
            return
        }

        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foregroundStarted = true
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Failed to enter foreground", securityException)
            stopSelf()
        }
    }

    private fun buildNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.lock_monitor_notification_title))
            .setContentText(getString(R.string.lock_monitor_notification_content))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannelO()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannelO() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.lock_monitor_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.lock_monitor_notification_content)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:WakeLock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        wakeLock = null
    }

    companion object {
        private const val TAG = "LockMonitorService"
        private const val NOTIFICATION_ID = 2001
        private const val NOTIFICATION_CHANNEL_ID = "lock_monitor"
        private const val RESTART_REQUEST_CODE = 9001
        private const val BLACKLIST_OVERLAY_DEBOUNCE_MILLIS = 1_000L
        private const val LOCK_UI_REDIRECT_DEBOUNCE_MILLIS = 1_500L

        fun start(context: Context) {
            val intent = Intent(context, LockMonitorService::class.java)
            val canStartForeground =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    context.hasPostNotificationPermissionCompat()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && canStartForeground) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                try {
                    @Suppress("DEPRECATION")
                    context.startService(intent)
                } catch (illegalStateException: IllegalStateException) {
                    Log.e(TAG, "Unable to start lock monitor service in background", illegalStateException)
                }
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LockMonitorService::class.java)
            context.stopService(intent)
        }
    }

    private fun supportsDeviceProtectedStorage(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    private fun isUserUnlocked(): Boolean {
        if (!supportsDeviceProtectedStorage()) return true
        val userManager = getSystemService(UserManager::class.java)
        return userManager?.isUserUnlocked ?: true
    }

}

private fun Context.hasPostNotificationPermissionCompat(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}
