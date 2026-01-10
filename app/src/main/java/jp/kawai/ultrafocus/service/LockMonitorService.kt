package jp.kawai.ultrafocus.service

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
import jp.kawai.ultrafocus.R
import jp.kawai.ultrafocus.data.repository.LockRepository
import jp.kawai.ultrafocus.data.repository.SettingsPackages
import jp.kawai.ultrafocus.emergency.EmergencyUnlockState
import jp.kawai.ultrafocus.emergency.EmergencyUnlockStateStore
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
    private val emergencyRedirectThrottler = PackageEventThrottler(EMERGENCY_REDIRECT_DEBOUNCE_MILLIS)
    private val allowedAppThrottler = PackageEventThrottler(ALLOWED_APP_HIDE_DEBOUNCE_MILLIS)
    @Volatile
    private var allowedAppForeground: Boolean = false
    @Volatile
    private var lastAllowedAppSeenElapsed: Long = 0L
    @Volatile
    private var pendingAllowedSessionExitPackage: String? = null
    @Volatile
    private var pendingAllowedSessionExitStartedAt: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reason = intent?.getStringExtra(EXTRA_START_REASON)
            ?: intent?.getStringExtra(ServiceRestartScheduler.EXTRA_START_REASON)
            ?: "unknown"
        val requestedAt = intent?.getLongExtra(EXTRA_REQUESTED_AT, System.currentTimeMillis())
            ?: System.currentTimeMillis()
        val sinceLast = previousStartWalltime?.let { requestedAt - it }?.takeIf { it >= 0 }
        val debounceHandled = intent?.getBooleanExtra(EXTRA_DEBOUNCE_HANDLED, false) ?: false
        if (!debounceHandled && shouldDebounce(reason, bypass = false)) {
            Log.d(
                TAG,
                "onStartCommand debounced reason=$reason sinceLastElapsed=${SystemClock.elapsedRealtime() - lastStartElapsedRealtime}ms"
            )
            return START_STICKY
        }
        Log.d(
            TAG,
            "onStartCommand reason=$reason requestedAt=$requestedAt sinceLast=${sinceLast ?: "-"}ms"
        )
        previousStartWalltime = requestedAt

        acquireWakeLock()
        lockRepository.refreshDynamicLists()
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
            serviceScope.launch { handleForegroundPackage(normalized) }
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
        if (!nextState) {
            allowedAppForeground = false
        }
        if (nextState) {
            // ロック開始時は確実に即時オーバーレイを掲出したいのでデバウンスを無視する
            overlayManager.show(bypassDebounce = true)
        }
    }

    private suspend fun handleForegroundPackage(packageName: String) {
        val nowElapsed = SystemClock.elapsedRealtime()
        if (EmergencyUnlockState.isActive() || EmergencyUnlockStateStore.isActive(this@LockMonitorService)) {
            if (packageName != this.packageName) {
                redirectToEmergencyUnlock(packageName, reason = "foreground_detected")
            }
            return
        }
        val launchInProgress = AllowedAppLaunchStore.isLaunchInProgress(this@LockMonitorService)
        var sessionActive = AllowedAppLaunchStore.isSessionActive(this@LockMonitorService)

        if (launchInProgress &&
            (packageName == this.packageName ||
                SettingsPackages.TRANSIENT_ONLY.contains(packageName) ||
                SettingsPackages.PERMISSION_CONTROLLER_ONLY.contains(packageName))
        ) {
            return
        }

        if (sessionActive &&
            (SettingsPackages.SYSTEM_UI_ONLY.contains(packageName) ||
                SettingsPackages.PERMISSION_CONTROLLER_ONLY.contains(packageName))
        ) {
            return
        }
        val allowedTargets = lockRepository.allowedAppTargets()
        val isTemporarilyAllowed = AllowedAppLaunchStore.isAllowed(this, packageName)
        val isAllowedApp = allowedTargets.isAllowed(packageName) || isTemporarilyAllowed
        if (isAllowedApp) {
            allowedAppForeground = true
            lastAllowedAppSeenElapsed = nowElapsed
            pendingAllowedSessionExitPackage = null
            pendingAllowedSessionExitStartedAt = 0L
            forcedRedirectJob?.cancel()
            AllowedAppLaunchStore.clearLaunch(this@LockMonitorService)
            AllowedAppLaunchStore.extendSession(this@LockMonitorService, ttlMillis = ALLOWED_APP_SESSION_TTL_MILLIS)
            if (allowedAppThrottler.shouldTrigger(packageName)) {
                OverlayLockService.setAllowedAppSuppressed(this@LockMonitorService, true)
            }
            return
        }

        if (allowedAppForeground && SettingsPackages.SYSTEM_UI_ONLY.contains(packageName)) {
            return
        }

        val inPermissionRecoverySettings = isPermissionRecoverySettings(packageName)
        if (sessionActive && SettingsPackages.LAUNCHERS_ONLY.contains(packageName)) {
            if (launchInProgress && !allowedAppForeground) {
                return
            }
            sessionActive = false
            allowedAppForeground = false
            lastAllowedAppSeenElapsed = 0L
            pendingAllowedSessionExitPackage = null
            pendingAllowedSessionExitStartedAt = 0L
            AllowedAppLaunchStore.clear(this@LockMonitorService)
            OverlayLockService.setAllowedAppSuppressed(this@LockMonitorService, false)
            if (!inPermissionRecoverySettings) {
                overlayManager.show(bypassDebounce = true)
            }
            return
        }
        if (sessionActive && !SettingsPackages.SYSTEM_UI_ONLY.contains(packageName)) {
            if (pendingAllowedSessionExitPackage == packageName &&
                nowElapsed - pendingAllowedSessionExitStartedAt >= ALLOWED_APP_SESSION_EXIT_CONFIRM_MILLIS
            ) {
                sessionActive = false
                allowedAppForeground = false
                lastAllowedAppSeenElapsed = 0L
                pendingAllowedSessionExitPackage = null
                pendingAllowedSessionExitStartedAt = 0L
                AllowedAppLaunchStore.clear(this@LockMonitorService)
                OverlayLockService.setAllowedAppSuppressed(this@LockMonitorService, false)
                if (!inPermissionRecoverySettings) {
                    overlayManager.show(bypassDebounce = true)
                }
            } else {
                pendingAllowedSessionExitPackage = packageName
                pendingAllowedSessionExitStartedAt = nowElapsed
                return
            }
        } else if (allowedAppForeground && packageName != this.packageName && !SettingsPackages.TRANSIENT_ONLY.contains(packageName)) {
            allowedAppForeground = false
            lastAllowedAppSeenElapsed = 0L
            pendingAllowedSessionExitPackage = null
            pendingAllowedSessionExitStartedAt = 0L
            AllowedAppLaunchStore.clear(this@LockMonitorService)
            OverlayLockService.setAllowedAppSuppressed(this@LockMonitorService, false)
            if (!inPermissionRecoverySettings) {
                overlayManager.show(bypassDebounce = true)
            }
        }

        if (launchInProgress || sessionActive) {
            return
        }

        if (inPermissionRecoverySettings) {
            Log.d(TAG, "Skip redirect during permission recovery for package=$packageName")
            return
        }

        if (lockRepository.shouldForceLockUi(packageName)) {
            val reason = resolveReasonLabel(packageName)
            handleForcedRedirect(packageName, reason)
        } else {
            handleBlacklistedPackage(packageName, reason = "disallowed_app")
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
        if (EmergencyUnlockState.isActive() || EmergencyUnlockStateStore.isActive(this@LockMonitorService)) {
            redirectToEmergencyUnlock(packageName, reason = "forced_redirect")
            return
        }
        startForcedRedirectBurst(packageName, reason)
    }

    private fun startForcedRedirectBurst(packageName: String, reason: String) {
        forcedRedirectJob?.cancel()
        forcedRedirectJob = serviceScope.launch {
            val start = SystemClock.elapsedRealtime()
            var iteration = 0
            while (isActive && isLocked && SystemClock.elapsedRealtime() - start <= FORCED_REDIRECT_BURST_MILLIS) {
                if (AllowedAppLaunchStore.isLaunchInProgress(this@LockMonitorService) ||
                    AllowedAppLaunchStore.isSessionActive(this@LockMonitorService)
                ) {
                    Log.d(TAG, "Stop redirect burst; allowed app session active")
                    break
                }
                iteration++
                if (EmergencyUnlockState.isActive() || EmergencyUnlockStateStore.isActive(this@LockMonitorService)) {
                    redirectToEmergencyUnlock(packageName, reason = "redirect_burst")
                    break
                }
                // Overlay 再掲出（デバウンス無効化）
                runCatching { overlayManager.show(bypassDebounce = true) }
                    .onFailure { Log.w(TAG, "Failed to force overlay (iteration=$iteration)", it) }
                // 自前 UI を最前面へ
                runCatching { lockUiLauncher.bringToFront(triggerPackage = packageName, triggerReason = reason) }
                    .onFailure { Log.w(TAG, "Failed to bring lock UI (iteration=$iteration)", it) }
                delay(FORCED_REDIRECT_INTERVAL_MILLIS)
            }
        }
    }

    private suspend fun redirectToEmergencyUnlock(packageName: String, reason: String) {
        if (emergencyRedirectThrottler.shouldTrigger(packageName)) {
            Log.d(TAG, "Emergency unlock active; redirect reason=$reason package=$packageName")
            lockUiLauncher.bringEmergencyUnlockToFront()
        } else {
            Log.v(TAG, "Skip emergency redirect (debounced) reason=$reason package=$packageName")
        }
    }

    private fun resolveReasonLabel(packageName: String): String {
        val normalized = packageName.trim()
        if (normalized.contains("permissioncontroller")) {
            return "permission_controller"
        }
        return "settings"
    }

    private fun isPermissionRecoverySettings(packageName: String): Boolean {
        return !hasPostNotificationPermissionCompat() &&
            PermissionRecoveryStore.isActive(this@LockMonitorService) &&
            SettingsPackages.SETTINGS_ONLY.contains(packageName)
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
        if (wakeLock?.isHeld == true) return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:WakeLock").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MILLIS)
        }
        Log.d(TAG, "WakeLock acquired timeout=${WAKE_LOCK_TIMEOUT_MILLIS}ms")
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
                Log.d(TAG, "WakeLock released")
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
        private const val EMERGENCY_REDIRECT_DEBOUNCE_MILLIS = 350L
        private const val ALLOWED_APP_HIDE_DEBOUNCE_MILLIS = 1_000L
        private const val ALLOWED_APP_SESSION_TTL_MILLIS = 60_000L
        private const val ALLOWED_APP_SESSION_EXIT_CONFIRM_MILLIS = 400L
        private const val FORCED_REDIRECT_BURST_MILLIS = 5_000L
        private const val FORCED_REDIRECT_INTERVAL_MILLIS = 400L
        private const val START_DEBOUNCE_MILLIS = 12_000L
        private const val RESTART_DEBOUNCE_MILLIS = 3_000L
        private const val RESTART_REASON = "service_restart"
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 180_000L
        private const val EXTRA_START_REASON = "extra_start_reason"
        private const val EXTRA_REQUESTED_AT = "extra_requested_at"
        private const val EXTRA_DEBOUNCE_HANDLED = "extra_debounce_handled"
        private var previousStartWalltime: Long? = null

        @Volatile
        private var lastStartElapsedRealtime: Long = 0L

        fun start(context: Context, reason: String = "unknown", bypassDebounce: Boolean = false) {
            if (shouldDebounce(reason, bypassDebounce)) return

            val appContext = context.applicationContext
            val intent = Intent(appContext, LockMonitorService::class.java).apply {
                putExtra(EXTRA_START_REASON, reason)
                putExtra(EXTRA_REQUESTED_AT, System.currentTimeMillis())
                putExtra(EXTRA_DEBOUNCE_HANDLED, true)
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

        private fun shouldDebounce(reason: String?, bypass: Boolean): Boolean {
            val nowElapsed = SystemClock.elapsedRealtime()
            return shouldDebounceInternal(bypass, reason, nowElapsed)
        }

        private fun shouldDebounceInternal(
            bypass: Boolean,
            reason: String?,
            nowElapsed: Long
        ): Boolean {
            if (bypass) return false
            val debounceMillis = if (reason == RESTART_REASON) RESTART_DEBOUNCE_MILLIS else START_DEBOUNCE_MILLIS
            val sinceLast = nowElapsed - lastStartElapsedRealtime
            if (lastStartElapsedRealtime != 0L && sinceLast < debounceMillis) {
                Log.d(TAG, "Skip start (debounced) reason=$reason sinceLast=${sinceLast}ms threshold=$debounceMillis")
                return true
            }
            lastStartElapsedRealtime = nowElapsed
            return false
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
