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
import android.os.SystemClock
import android.os.UserManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.smartphone_lock.R
import com.example.smartphone_lock.service.EmergencyUnlockCoordinator
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    private var forcedRedirectJob: Job? = null
    private val overlayThrottler = PackageEventThrottler(BLACKLIST_OVERLAY_DEBOUNCE_MILLIS)
    private val lockUiRedirectThrottler = PackageEventThrottler(LOCK_UI_REDIRECT_DEBOUNCE_MILLIS)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reason = intent?.getStringExtra(EXTRA_START_REASON) ?: "unknown"
        val requestedAt = intent?.getLongExtra(EXTRA_REQUESTED_AT, System.currentTimeMillis())
            ?: System.currentTimeMillis()
        val sinceLast = previousStartWalltime?.let { requestedAt - it }?.takeIf { it >= 0 }
        Log.d(
            TAG,
            "onStartCommand reason=$reason requestedAt=$requestedAt sinceLast=${sinceLast ?: "-"}ms"
        )
        previousStartWalltime = requestedAt

        ensureForeground(reason)
        startMonitoring()
        demoteForegroundIfNeeded("startCommand")
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
            if (EmergencyUnlockCoordinator.isInProgress()) {
                Log.d(TAG, "Overlay suppressed during emergency unlock")
            } else {
                // ロック開始時は確実に即時オーバーレイを掲出したいのでデバウンスを無視する
                overlayManager.show(bypassDebounce = true)
            }
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
        startForcedRedirectBurst(packageName, reason)
    }

    private fun startForcedRedirectBurst(packageName: String, reason: String) {
        forcedRedirectJob?.cancel()
        forcedRedirectJob = serviceScope.launch {
            val start = SystemClock.elapsedRealtime()
            var iteration = 0
            while (isActive && isLocked && SystemClock.elapsedRealtime() - start <= FORCED_REDIRECT_BURST_MILLIS) {
                iteration++
                // Overlay 再掲出（デバウンス無効化）
                runCatching { overlayManager.show(bypassDebounce = true) }
                    .onFailure { Log.w(TAG, "Failed to force overlay (iteration=$iteration)", it) }
                delay(FORCED_REDIRECT_INTERVAL_MILLIS)
            }
        }
    }

    private fun resolveReasonLabel(packageName: String): String {
        val normalized = packageName.trim()
        if (normalized.contains("permissioncontroller")) {
            return "permission_controller"
        }
        return "settings"
    }

    private fun ensureForeground(reason: String) {
        if (foregroundStarted) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotificationPermissionCompat()) {
            Log.w(TAG, "Notification permission missing; running monitor without foreground")
            foregroundStarted = true
            return
        }

        val notification = buildNotification()
        try {
            val foregroundType = resolveForegroundType()
            if (foregroundType != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, foregroundType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foregroundStarted = true
            Log.d(TAG, "Entered foreground (reason=$reason type=${foregroundType ?: "none"})")
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Failed to enter foreground", securityException)
            stopSelf()
        }
    }

    private fun demoteForegroundIfNeeded(reason: String) {
        if (!foregroundStarted) return
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }.onFailure {
            Log.w(TAG, "Failed to stop foreground ($reason)", it)
        }
        foregroundStarted = false
        Log.d(TAG, "Foreground removed (reason=$reason)")
    }

    private fun resolveForegroundType(): Int? {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> null
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
            acquire(WAKE_LOCK_TIMEOUT_MILLIS)
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
        private const val FORCED_REDIRECT_BURST_MILLIS = 5_000L
        private const val FORCED_REDIRECT_INTERVAL_MILLIS = 400L
        private const val START_DEBOUNCE_MILLIS = 12_000L
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 15_000L
        private const val EXTRA_START_REASON = "extra_start_reason"
        private const val EXTRA_REQUESTED_AT = "extra_requested_at"
        private var previousStartWalltime: Long? = null

        @Volatile
        private var lastStartElapsedRealtime: Long = 0L

        fun start(context: Context, reason: String = "unknown", bypassDebounce: Boolean = false) {
            val nowElapsed = SystemClock.elapsedRealtime()
            val sinceLast = nowElapsed - lastStartElapsedRealtime
            if (!bypassDebounce && lastStartElapsedRealtime != 0L && sinceLast < START_DEBOUNCE_MILLIS) {
                Log.d(TAG, "Skip start (debounced) reason=$reason sinceLast=${sinceLast}ms")
                return
            }
            lastStartElapsedRealtime = nowElapsed

            val appContext = context.applicationContext
            val intent = Intent(appContext, LockMonitorService::class.java).apply {
                putExtra(EXTRA_START_REASON, reason)
                putExtra(EXTRA_REQUESTED_AT, System.currentTimeMillis())
            }
            val canStartForeground =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    appContext.hasPostNotificationPermissionCompat()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && canStartForeground) {
                runCatching { ContextCompat.startForegroundService(appContext, intent) }
                    .onFailure { Log.e(TAG, "Unable to start lock monitor service", it) }
            } else {
                runCatching {
                    @Suppress("DEPRECATION")
                    appContext.startService(intent)
                }.onFailure { throwable ->
                    Log.e(TAG, "Unable to start lock monitor service", throwable)
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
