package jp.kawai.ultrafocus.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserManager
import android.net.Uri
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.app.AppOpsManager
import android.os.Process
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import jp.kawai.ultrafocus.BuildConfig
import jp.kawai.ultrafocus.MainActivity
import jp.kawai.ultrafocus.R
import jp.kawai.ultrafocus.data.repository.SettingsPackages
import jp.kawai.ultrafocus.data.repository.LockRepository
import jp.kawai.ultrafocus.emergency.EmergencyUnlockState
import jp.kawai.ultrafocus.emergency.EmergencyUnlockStateStore
import jp.kawai.ultrafocus.navigation.AppDestination
import jp.kawai.ultrafocus.util.AllowedAppResolver
import jp.kawai.ultrafocus.data.datastore.DataStoreManager
import jp.kawai.ultrafocus.data.datastore.LockStatePreferences
import jp.kawai.ultrafocus.util.formatLockRemainingTime
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class OverlayLockService : Service() {

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var foregroundEventSource: ForegroundAppEventSource

    @Inject
    lateinit var lockRepository: LockRepository


    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var overlayContainer: FrameLayout? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var contentContainer: LinearLayout? = null
    private var countdownTextView: TextView? = null
    private var titleTextView: TextView? = null
    private var messageTextView: TextView? = null
    private var emergencyButton: Button? = null
    private var permissionButton: Button? = null
    private var allowedAppsRow: LinearLayout? = null
    private var lockStateJob: Job? = null
    private var deviceProtectedLockStateJob: Job? = null
    private var countdownJob: Job? = null
    private var userUnlockWatcherJob: Job? = null
    private var deviceProtectedSnapshot: LockStatePreferences? = null
    private var foregroundStarted = false
    private var latestLockState: LockStatePreferences? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var overlayMode: OverlayMode = OverlayMode.LOCK
    private var permissionRecoveryMonitorJob: Job? = null
    private var permissionRecoveryFallbackJob: Job? = null
    private var permissionRecoveryInProgress: Boolean = false
    private var settingsInForeground: Boolean = false
    private var allowedAppSuppressed: Boolean = false
    private var allowedAppSuppressedAtElapsed: Long = 0L

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reason = intent?.getStringExtra(EXTRA_START_REASON) ?: "unknown"
        val requestedAt = intent?.getLongExtra(EXTRA_REQUESTED_AT, System.currentTimeMillis())
            ?: System.currentTimeMillis()
        val forceShow = intent?.getBooleanExtra(EXTRA_FORCE_SHOW, false) ?: false
        val sinceLast = previousStartWalltime?.let { requestedAt - it }?.takeIf { it >= 0 }
        Log.d(
            TAG,
            "onStartCommand action=${intent?.action} reason=$reason requestedAt=$requestedAt sinceLast=${sinceLast ?: "-"}ms"
        )
        previousStartWalltime = requestedAt
        if (forceShow) {
            endPermissionRecovery()
        } else {
            restorePermissionRecoveryIfNeeded()
        }

        when (intent?.action) {
            ACTION_START, null -> startLockDisplay()
            ACTION_SUPPRESS_ALLOWED_APP -> {
                allowedAppSuppressed = true
                allowedAppSuppressedAtElapsed = SystemClock.elapsedRealtime()
                startLockDisplay()
                applyAllowedAppSuppression(true)
            }
            ACTION_RELEASE_ALLOWED_APP -> {
                startLockDisplay()
                applyAllowedAppSuppression(false)
            }
            else -> Log.w(TAG, "Unknown action received: ${intent?.action}")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLockDisplay(clearState = false)
        serviceScope.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        ServiceRestartScheduler.schedule(this, OverlayLockService::class.java, RESTART_REQUEST_CODE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLockDisplay() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission missing; stopping service")
            stopLockDisplay()
            return
        }
        acquireWakeLock()
        ensureForeground()
        startDeviceProtectedLockCollection()
        startCredentialEncryptedLockCollectionIfUnlocked()
        latestLockState?.let { handleLockState(it) }
        demoteForegroundIfNeeded("lockDisplay")
    }

    private fun stopLockDisplay(clearState: Boolean = true) {
        countdownJob?.cancel()
        countdownJob = null
        lockStateJob?.cancel()
        lockStateJob = null
        deviceProtectedLockStateJob?.cancel()
        deviceProtectedLockStateJob = null
        userUnlockWatcherJob?.cancel()
        userUnlockWatcherJob = null
        endPermissionRecovery()
        allowedAppSuppressed = false
        latestLockState = null
        hideOverlay()
        releaseWakeLock()
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        if (clearState) {
            stopSelf()
        }
    }

    private fun startDeviceProtectedLockCollection() {
        if (!supportsDeviceProtectedStorage()) return
        if (isUserUnlocked()) {
            deviceProtectedLockStateJob?.cancel()
            deviceProtectedLockStateJob = null
            startCredentialEncryptedLockCollectionIfUnlocked()
            return
        }
        if (deviceProtectedLockStateJob != null) return
        deviceProtectedLockStateJob = serviceScope.launch {
            dataStoreManager.deviceProtectedLockState.collectLatest { state ->
                deviceProtectedSnapshot = LockStatePreferences(
                    isLocked = state.isLocked,
                    lockStartTimestamp = state.lockStartTimestamp,
                    lockEndTimestamp = state.lockEndTimestamp
                )
                if (isUserUnlocked()) {
                    startCredentialEncryptedLockCollectionIfUnlocked()
                    cancel("User unlocked; DP overlay updates no longer needed")
                } else {
                    handleLockState(deviceProtectedSnapshot!!)
                }
            }
        }
    }

    private fun startCredentialEncryptedLockCollectionIfUnlocked() {
        if (!isUserUnlocked()) {
            // CE ストアがまだ開かれていない場合は DP ストアのみで運用し、解錠後に再度試行する
            scheduleUserUnlockWatcher()
            return
        }
        if (lockStateJob != null) return
        lockStateJob = serviceScope.launch {
            try {
                dataStoreManager.lockState.collectLatest { state ->
                    handleLockState(state)
                }
            } catch (throwable: Throwable) {
                if (throwable is IllegalStateException) {
                    // ユーザー未解錠等で CE にアクセスできない場合は DP スナップショットにフォールバックして継続
                    Log.w(TAG, "CE lock state unavailable; fallback to DP snapshot until unlock", throwable)
                    startCredentialEncryptedLockCollectionIfUnlocked()
                } else {
                    throw throwable
                }
            }
        }
    }

    private fun scheduleUserUnlockWatcher() {
        if (userUnlockWatcherJob?.isActive == true) return
        userUnlockWatcherJob = serviceScope.launch {
            while (isActive && !isUserUnlocked()) {
                delay(COUNTDOWN_INTERVAL_MILLIS)
            }
            if (isActive) {
                startCredentialEncryptedLockCollectionIfUnlocked()
            }
        }
    }

    private fun handleLockState(state: LockStatePreferences) {
        val now = System.currentTimeMillis()
        val fallbackLocked = deviceProtectedSnapshot?.let { dp ->
            dp.isLocked && (dp.lockEndTimestamp == null || dp.lockEndTimestamp > now)
        } == true
        val effectiveState = when {
            state.isLocked -> state
            fallbackLocked -> deviceProtectedSnapshot!!
            else -> state
        }

        latestLockState = effectiveState
        if (effectiveState.isLocked && effectiveState.lockEndTimestamp != null) {
            ensureForeground()
            val mode = resolveOverlayMode()
            if (shouldShowOverlay(mode)) {
                showOverlayIfNeeded()
            }
            updateOverlayModeIfNeeded(mode)
            restartCountdown(effectiveState.lockEndTimestamp)
            demoteForegroundIfNeeded("lock_state_update")
        } else {
            WatchdogScheduler.cancelHeartbeat(this)
            WatchdogScheduler.cancelLockExpiry(this)
            stopLockDisplay()
        }
    }

    private fun restartCountdown(lockEndTimestamp: Long) {
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            while (isActive) {
                val remainingMillis = (lockEndTimestamp - System.currentTimeMillis()).coerceAtLeast(0L)
                updateCountdown(remainingMillis)
                if (remainingMillis <= 0L) {
                    dataStoreManager.updateLockState(false, null, null)
                    break
                }
                delay(COUNTDOWN_INTERVAL_MILLIS)
            }
        }
    }

    private fun updateCountdown(remainingMillis: Long) {
        val mode = resolveOverlayMode()
        if (allowedAppSuppressed) {
            if (!AllowedAppLaunchStore.isSessionActive(this) && isAllowedAppSuppressionStale()) {
                allowedAppSuppressed = false
                allowedAppSuppressedAtElapsed = 0L
                applyOverlaySuppressionState(suppressed = false)
            } else {
                applyOverlaySuppressionState(suppressed = true)
                return
            }
        }
        if (!allowedAppSuppressed) {
            if (permissionRecoveryInProgress && mode == OverlayMode.LOCK) {
                endPermissionRecovery()
                showOverlayIfNeeded()
            }
            if (overlayContainer == null && shouldShowOverlay(mode)) {
                showOverlayIfNeeded()
            }
        }
        updateOverlayModeIfNeeded(mode)
        if (mode == OverlayMode.PERMISSION_RECOVERY) {
            return
        }
        val formatted = formatLockRemainingTime(this, remainingMillis)
        countdownTextView?.text = formatted
        if (hasPostNotificationPermission()) {
            val notification = buildNotification(formatted)
            try {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            } catch (security: SecurityException) {
                Log.w(TAG, "Notification permission denied at runtime; skipping update", security)
            }
        }
    }

    private fun launchEmergencyUnlock() {
        EmergencyUnlockState.setActive(true)
        EmergencyUnlockStateStore.setActive(this, true)
        stopLockDisplay(clearState = true)
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAV_ROUTE, AppDestination.EmergencyUnlock.route)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }
        runCatching { startActivity(intent) }
            .onFailure { throwable ->
                EmergencyUnlockState.setActive(false)
                EmergencyUnlockStateStore.setActive(this, false)
                Log.w(TAG, "Failed to launch emergency unlock", throwable)
                start(this, reason = "emergency_unlock_failed", bypassDebounce = true)
            }
    }

    private fun showOverlayIfNeeded() {
        if (overlayContainer != null) return
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        val metrics = resources.displayMetrics
        val container = FrameLayout(this).apply {
            background = buildLockBackground()
            isClickable = true
            isFocusable = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                dpToPx(12f, metrics),
                dpToPx(12f, metrics),
                dpToPx(12f, metrics),
                dpToPx(12f, metrics)
            )
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.overlay_lock_panel_title)
            setTextColor(COLOR_TEXT_DARK_NAVY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setIncludeFontPadding(false)
        }

        val messageView = TextView(this).apply {
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setIncludeFontPadding(false)
            visibility = View.GONE
        }

        val textView = TextView(this).apply {
            setTextColor(COLOR_TEXT_DARK_NAVY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setFontFeatureSettings("tnum")
            gravity = Gravity.CENTER
            setIncludeFontPadding(false)
        }

        content.addView(
            titleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        content.addView(
            messageView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(6f, metrics)
                bottomMargin = dpToPx(6f, metrics)
            }
        )
        content.addView(
            textView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8f, metrics)
                bottomMargin = dpToPx(8f, metrics)
            }
        )

        val emergencyButtonView = Button(this).apply {
            text = getString(R.string.overlay_lock_emergency_unlock)
            setTextColor(COLOR_CLEAN_WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpToPx(14f, metrics).toFloat()
                setColor(COLOR_TEXT_DARK_NAVY)
            }
            setPadding(
                dpToPx(16f, metrics),
                dpToPx(10f, metrics),
                dpToPx(16f, metrics),
                dpToPx(10f, metrics)
            )
            setOnClickListener { launchEmergencyUnlock() }
        }
        content.addView(
            emergencyButtonView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12f, metrics)
            }
        )

        val permissionButtonView = Button(this).apply {
            text = getString(R.string.overlay_lock_permission_button)
            setTextColor(COLOR_CLEAN_WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpToPx(14f, metrics).toFloat()
                setColor(COLOR_TEXT_DARK_NAVY)
            }
            setPadding(
                dpToPx(16f, metrics),
                dpToPx(10f, metrics),
                dpToPx(16f, metrics),
                dpToPx(10f, metrics)
            )
            setOnClickListener { launchNotificationPermissionSettings() }
            visibility = View.GONE
        }
        content.addView(
            permissionButtonView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12f, metrics)
            }
        )

        val shortcutsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val phoneShortcut = buildAllowedAppShortcut(
            label = getString(R.string.lock_overlay_shortcut_phone),
            iconRes = android.R.drawable.ic_menu_call
        ) { launchDialer() }
        val smsShortcut = buildAllowedAppShortcut(
            label = getString(R.string.lock_overlay_shortcut_sms),
            iconRes = android.R.drawable.ic_menu_send
        ) { launchSms() }
        shortcutsRow.addView(
            phoneShortcut,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        shortcutsRow.addView(
            smsShortcut,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dpToPx(20f, metrics)
            }
        )
        content.addView(
            shortcutsRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12f, metrics)
            }
        )

        if (BuildConfig.DEBUG) {
            val debugButton = Button(this).apply {
                text = getString(R.string.lock_screen_dev_force_unlock)
                setTextColor(COLOR_CLEAN_WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dpToPx(12f, metrics).toFloat()
                    setColor(COLOR_WARNING_RED)
                }
                setPadding(
                    dpToPx(12f, metrics),
                    dpToPx(10f, metrics),
                    dpToPx(12f, metrics),
                    dpToPx(10f, metrics)
                )
                setOnClickListener {
                    serviceScope.launch {
                        dataStoreManager.updateLockState(false, null, null)
                    }
                }
            }
            content.addView(
                debugButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(16f, metrics)
                }
            )
        }

        container.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                val margin = dpToPx(16f, metrics)
                setMargins(margin, margin, margin, margin)
            }
        )
        overlayContainer = container
        overlayLayoutParams = layoutParams
        contentContainer = content
        countdownTextView = textView
        titleTextView = titleView
        messageTextView = messageView
        emergencyButton = emergencyButtonView
        permissionButton = permissionButtonView
        allowedAppsRow = shortcutsRow
        applyOverlayMode(overlayMode)
        if (allowedAppSuppressed) {
            val touchableFlag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            layoutParams.flags = layoutParams.flags or touchableFlag
            container.alpha = 0f
            container.isClickable = false
            container.isFocusable = false
        }
        windowManager.addView(container, layoutParams)
        if (allowedAppSuppressed) {
            applyOverlaySuppressionState(suppressed = true)
        }
    }

    private fun hideOverlay() {
        countdownTextView = null
        titleTextView = null
        messageTextView = null
        emergencyButton = null
        permissionButton = null
        allowedAppsRow = null
        contentContainer = null
        overlayContainer?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayContainer = null
        overlayLayoutParams = null
    }

    private fun resolveOverlayMode(): OverlayMode {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotificationPermission()) {
            OverlayMode.PERMISSION_RECOVERY
        } else {
            OverlayMode.LOCK
        }
    }

    private fun updateOverlayModeIfNeeded(nextMode: OverlayMode) {
        if (overlayMode == nextMode) return
        overlayMode = nextMode
        applyOverlayMode(nextMode)
    }

    private fun applyOverlayMode(mode: OverlayMode) {
        when (mode) {
            OverlayMode.LOCK -> {
                titleTextView?.text = getString(R.string.overlay_lock_panel_title)
                messageTextView?.visibility = View.GONE
                countdownTextView?.visibility = View.VISIBLE
                emergencyButton?.visibility = View.VISIBLE
                permissionButton?.visibility = View.GONE
                allowedAppsRow?.visibility = View.VISIBLE
                updateOverlayBackground(lockMode = true)
            }

            OverlayMode.PERMISSION_RECOVERY -> {
                titleTextView?.text = getString(R.string.overlay_lock_permission_title)
                messageTextView?.text = getString(R.string.overlay_lock_permission_message)
                messageTextView?.visibility = View.VISIBLE
                countdownTextView?.visibility = View.GONE
                emergencyButton?.visibility = View.GONE
                permissionButton?.visibility = View.VISIBLE
                allowedAppsRow?.visibility = View.GONE
                updateOverlayBackground(lockMode = false)
            }
        }
    }

    private fun applyAllowedAppSuppression(suppressed: Boolean) {
        if (suppressed) {
            allowedAppSuppressedAtElapsed = SystemClock.elapsedRealtime()
            allowedAppSuppressed = true
            applyOverlaySuppressionState(suppressed = true)
            return
        }
        if (allowedAppSuppressed == suppressed) {
            allowedAppSuppressedAtElapsed = 0L
            return
        }
        allowedAppSuppressed = false
        allowedAppSuppressedAtElapsed = 0L
        val mode = resolveOverlayMode()
        if (shouldShowOverlay(mode)) {
            showOverlayIfNeeded()
        }
        applyOverlaySuppressionState(suppressed = false)
        updateOverlayModeIfNeeded(mode)
    }

    private fun isAllowedAppSuppressionStale(): Boolean {
        if (!allowedAppSuppressed) return false
        val lastSignal = allowedAppSuppressedAtElapsed
        if (lastSignal == 0L) return true
        val age = SystemClock.elapsedRealtime() - lastSignal
        return age > ALLOWED_APP_SUPPRESSION_TIMEOUT_MILLIS
    }

    private fun applyOverlaySuppressionState(suppressed: Boolean) {
        val container = overlayContainer ?: return
        val params = overlayLayoutParams ?: return
        val touchableFlag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (suppressed) {
            params.flags = params.flags or touchableFlag
            container.alpha = 0f
            container.isClickable = false
            container.isFocusable = false
        } else {
            params.flags = params.flags and touchableFlag.inv()
            container.alpha = 1f
            container.isClickable = true
            container.isFocusable = true
        }
        runCatching { windowManager.updateViewLayout(container, params) }
    }

    private fun launchNotificationPermissionSettings() {
        beginPermissionRecovery()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
            .onFailure { throwable -> Log.w(TAG, "Failed to open app settings", throwable) }
    }

    private fun buildAllowedAppShortcut(
        label: String,
        iconRes: Int,
        onClick: () -> Unit
    ): LinearLayout {
        val metrics = resources.displayMetrics
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val iconContainer = FrameLayout(this).apply {
            background = buildShortcutBackground(metrics)
        }
        val iconSize = dpToPx(22f, metrics)
        val iconView = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(COLOR_TEXT_DARK_NAVY)
            contentDescription = label
        }
        iconContainer.addView(
            iconView,
            FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
        )
        container.addView(
            iconContainer,
            LinearLayout.LayoutParams(
                dpToPx(52f, metrics),
                dpToPx(52f, metrics)
            )
        )
        val labelView = TextView(this).apply {
            text = label
            setTextColor(COLOR_TEXT_DARK_NAVY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setIncludeFontPadding(false)
        }
        container.addView(
            labelView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(6f, metrics)
            }
        )
        return container
    }

    private fun buildShortcutBackground(metrics: DisplayMetrics): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dpToPx(16f, metrics).toFloat()
            setColor(COLOR_CLEAN_WHITE)
        }
    }

    private fun launchDialer() {
        if (!isLockActive() || EmergencyUnlockState.isActive()) return
        lockRepository.refreshDynamicLists()
        applyAllowedAppSuppression(true)
        AllowedAppLaunchStore.startLaunch(this)
        AllowedAppLaunchStore.startSession(this)
        val targetPackage = AllowedAppResolver.resolveDialerPackage(
            this,
            lockRepository.allowedAppTargets().dialerPackage
        )
        if (targetPackage.isNullOrBlank()) {
            AllowedAppLaunchStore.clear(this)
            applyAllowedAppSuppression(false)
            Log.w(TAG, "No default dialer resolved; skip launch")
            return
        }
        AllowedAppLaunchStore.setAllowed(this, targetPackage)
        runCatching { AllowedAppLauncherActivity.start(this, AllowedAppLauncherActivity.Target.DIALER, targetPackage) }
            .onFailure { throwable ->
            applyAllowedAppSuppression(false)
            AllowedAppLaunchStore.clearSession(this)
            Log.w(TAG, "Failed to open dialer", throwable)
        }
    }

    private fun launchSms() {
        if (!isLockActive() || EmergencyUnlockState.isActive()) return
        lockRepository.refreshDynamicLists()
        applyAllowedAppSuppression(true)
        AllowedAppLaunchStore.startLaunch(this)
        AllowedAppLaunchStore.startSession(this)
        val targetPackage = AllowedAppResolver.resolveSmsPackage(
            this,
            lockRepository.allowedAppTargets().smsPackage
        )
        if (targetPackage.isNullOrBlank()) {
            AllowedAppLaunchStore.clear(this)
            applyAllowedAppSuppression(false)
            Log.w(TAG, "No default sms app resolved; skip launch")
            return
        }
        AllowedAppLaunchStore.setAllowed(this, targetPackage)
        runCatching { AllowedAppLauncherActivity.start(this, AllowedAppLauncherActivity.Target.SMS, targetPackage) }
            .onFailure { throwable ->
            applyAllowedAppSuppression(false)
            AllowedAppLaunchStore.clearSession(this)
            Log.w(TAG, "Failed to open sms", throwable)
        }
    }

    private fun updateOverlayBackground(lockMode: Boolean) {
        val container = overlayContainer ?: return
        if (lockMode) {
            container.background = buildLockBackground()
            contentContainer?.background = null
        } else {
            container.background = buildLockBackground()
            contentContainer?.background = buildPermissionCardBackground()
        }
    }

    private fun beginPermissionRecovery() {
        permissionRecoveryInProgress = true
        PermissionRecoveryStore.setActive(this, true)
        settingsInForeground = false
        LockMonitorService.start(this, reason = "permission_recovery", bypassDebounce = true)
        if (hasUsageStatsPermission()) {
            startPermissionRecoveryMonitor()
        } else {
            Log.w(TAG, "Usage access missing; fallback to timed recovery window")
            startPermissionRecoveryFallback()
        }
    }

    private fun shouldShowOverlay(mode: OverlayMode): Boolean {
        if (overlayContainer != null) return false
        if (allowedAppSuppressed) return false
        if (permissionRecoveryInProgress && settingsInForeground && mode == OverlayMode.PERMISSION_RECOVERY) {
            return false
        }
        return true
    }

    private fun isLockActive(): Boolean {
        val state = latestLockState
        val now = System.currentTimeMillis()
        return state?.isLocked == true && state.lockEndTimestamp?.let { it > now } != false
    }

    private fun startPermissionRecoveryMonitor() {
        if (permissionRecoveryMonitorJob?.isActive == true) return
        permissionRecoveryMonitorJob = serviceScope.launch(Dispatchers.Default) {
            var lastSettingsState: Boolean? = null
            var lastSettingsSeenElapsed = 0L
            var lastForegroundPackage: String? = null
            while (isActive && permissionRecoveryInProgress) {
                if (resolveOverlayMode() == OverlayMode.LOCK) {
                    withContext(Dispatchers.Main.immediate) {
                        endPermissionRecovery()
                        showOverlayIfNeeded()
                        updateOverlayModeIfNeeded(resolveOverlayMode())
                    }
                    break
                }
                val queryResult = queryForegroundPackage(lastForegroundPackage)
                if (queryResult.fallbackToTimed) {
                    withContext(Dispatchers.Main.immediate) {
                        if (permissionRecoveryInProgress) {
                            startPermissionRecoveryFallback()
                        }
                    }
                    break
                }
                val foregroundPackage = queryResult.packageName
                if (foregroundPackage != null) {
                    lastForegroundPackage = foregroundPackage
                }
                val nowElapsed = SystemClock.elapsedRealtime()
                val isSettingsNow = foregroundPackage != null && SettingsPackages.SETTINGS_ONLY.contains(foregroundPackage)
                if (isSettingsNow) {
                    lastSettingsSeenElapsed = nowElapsed
                }
                val stickySettings = nowElapsed - lastSettingsSeenElapsed < SETTINGS_STICKY_MILLIS
                if (lastSettingsState == null || lastSettingsState != stickySettings) {
                    lastSettingsState = stickySettings
                    settingsInForeground = stickySettings
                    withContext(Dispatchers.Main.immediate) {
                        if (stickySettings) {
                            hideOverlay()
                        } else if (shouldShowOverlay(resolveOverlayMode())) {
                            showOverlayIfNeeded()
                            updateOverlayModeIfNeeded(resolveOverlayMode())
                        }
                    }
                }
                delay(PERMISSION_RECOVERY_POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun startPermissionRecoveryFallback() {
        if (permissionRecoveryFallbackJob?.isActive == true) return
        hideOverlay()
        permissionRecoveryFallbackJob = serviceScope.launch {
            delay(PERMISSION_RECOVERY_FALLBACK_HIDE_MILLIS)
            if (resolveOverlayMode() == OverlayMode.PERMISSION_RECOVERY && isLockActive()) {
                showOverlayIfNeeded()
                updateOverlayModeIfNeeded(resolveOverlayMode())
            }
        }
    }

    private fun queryForegroundPackage(lastKnownPackage: String?): ForegroundQueryResult {
        var lastPackage: String? = lastKnownPackage
        try {
            foregroundEventSource.collectRecentEvents(PERMISSION_RECOVERY_QUERY_WINDOW_MILLIS) { pkg ->
                lastPackage = pkg
            }
        } catch (security: SecurityException) {
            Log.w(TAG, "Usage access permission missing during recovery", security)
            return ForegroundQueryResult(packageName = lastKnownPackage, fallbackToTimed = true)
        } catch (throwable: Throwable) {
            Log.w(TAG, "Failed to query foreground package during recovery", throwable)
            return ForegroundQueryResult(packageName = lastKnownPackage, fallbackToTimed = false)
        }
        val normalized = lastPackage?.trim()?.takeIf { it.isNotEmpty() }
        return ForegroundQueryResult(packageName = normalized, fallbackToTimed = false)
    }

    private fun endPermissionRecovery() {
        permissionRecoveryInProgress = false
        settingsInForeground = false
        PermissionRecoveryStore.setActive(this, false)
        permissionRecoveryMonitorJob?.cancel()
        permissionRecoveryMonitorJob = null
        permissionRecoveryFallbackJob?.cancel()
        permissionRecoveryFallbackJob = null
    }

    private fun restorePermissionRecoveryIfNeeded() {
        if (permissionRecoveryInProgress) return
        if (!PermissionRecoveryStore.isActive(this)) return
        if (resolveOverlayMode() == OverlayMode.LOCK) {
            PermissionRecoveryStore.setActive(this, false)
            return
        }
        permissionRecoveryInProgress = true
        settingsInForeground = false
        if (hasUsageStatsPermission()) {
            startPermissionRecoveryMonitor()
        } else {
            startPermissionRecoveryFallback()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_FOREGROUND
    }

    private fun buildLockBackground(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(COLOR_GRADIENT_END, COLOR_GRADIENT_START)
        )
    }

    private fun buildPermissionCardBackground(): android.graphics.drawable.GradientDrawable {
        val metrics = resources.displayMetrics
        return android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dpToPx(18f, metrics).toFloat()
            setColor(COLOR_CLEAN_WHITE)
        }
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

    private fun ensureForeground() {
        if (foregroundStarted) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotificationPermission()) {
            Log.w(TAG, "Notification permission missing; running without foreground")
            foregroundStarted = true
            return
        }
        val notification = buildNotification(getString(R.string.overlay_service_notification_content))
        try {
            val foregroundType = resolveForegroundType()
            if (foregroundType != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, foregroundType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foregroundStarted = true
            Log.d(TAG, "Entered foreground for overlay type=${foregroundType ?: "none"}")
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Failed to enter foreground", securityException)
            stopSelf()
        }
    }

    private fun demoteForegroundIfNeeded(reason: String) {
        if (!foregroundStarted) return
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            .onFailure { Log.w(TAG, "Failed to stop foreground ($reason)", it) }
        foregroundStarted = false
        Log.d(TAG, "Foreground removed (reason=$reason)")
    }

    private fun buildNotification(contentText: String) = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setContentTitle(getString(R.string.overlay_service_notification_title))
        .setContentText(contentText)
        .setSmallIcon(android.R.drawable.ic_lock_lock)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannelO()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannelO() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.overlay_service_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = getString(R.string.overlay_service_notification_content)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun hasPostNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun resolveForegroundType(): Int? {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> null
        }
    }

    private fun dpToPx(value: Float, metrics: DisplayMetrics): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics).toInt()

    companion object {
        private const val TAG = "OverlayLockService"
        private const val COUNTDOWN_INTERVAL_MILLIS = 1_000L
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "lock_overlay"
        private const val RESTART_REQUEST_CODE = 9002
        private const val START_DEBOUNCE_MILLIS = 12_000L
        private const val RESTART_DEBOUNCE_MILLIS = 3_000L
        private const val RESTART_REASON = "service_restart"
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 180_000L
        private const val PERMISSION_RECOVERY_QUERY_WINDOW_MILLIS = 2_000L
        private const val PERMISSION_RECOVERY_POLL_INTERVAL_MILLIS = 750L
        private const val PERMISSION_RECOVERY_FALLBACK_HIDE_MILLIS = 8_000L
        private const val SETTINGS_STICKY_MILLIS = 1_500L
        private const val ALLOWED_APP_SUPPRESSION_TIMEOUT_MILLIS = 2_500L
        private const val EXTRA_START_REASON = "extra_start_reason"
        private const val EXTRA_REQUESTED_AT = "extra_requested_at"
        private const val EXTRA_DEBOUNCE_HANDLED = "extra_debounce_handled"
        private const val EXTRA_FORCE_SHOW = "extra_force_show"
        private var previousStartWalltime: Long? = null

        @Volatile
        private var lastStartElapsedRealtime: Long = 0L

        // Sky Concept Colors (aligned with concept.md)
        private val COLOR_GRADIENT_START = Color.parseColor("#E6F2FF")
        private val COLOR_GRADIENT_END = Color.parseColor("#0A84FF")
        private val COLOR_TEXT_DARK_NAVY = Color.parseColor("#0B1A2D")
        private val COLOR_TEXT_SECONDARY = Color.parseColor("#8291A8")
        private val COLOR_WARNING_RED = Color.parseColor("#FF3B30")
        private val COLOR_CLEAN_WHITE = Color.WHITE

        const val ACTION_START = "jp.kawai.ultrafocus.action.START_LOCK"
        private const val ACTION_SUPPRESS_ALLOWED_APP = "jp.kawai.ultrafocus.action.SUPPRESS_ALLOWED_APP"
        private const val ACTION_RELEASE_ALLOWED_APP = "jp.kawai.ultrafocus.action.RELEASE_ALLOWED_APP"

        fun start(
            context: Context,
            reason: String = "unknown",
            bypassDebounce: Boolean = false,
            forceShow: Boolean = false
        ) {
            if (shouldDebounce(reason, bypassDebounce)) return
            if (EmergencyUnlockState.isActive() || EmergencyUnlockStateStore.isActive(context)) {
                Log.d(TAG, "Skip overlay start; emergency unlock active (reason=$reason)")
                return
            }

            val appContext = context.applicationContext
            val intent = Intent(appContext, OverlayLockService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_START_REASON, reason)
                putExtra(EXTRA_REQUESTED_AT, System.currentTimeMillis())
                putExtra(EXTRA_DEBOUNCE_HANDLED, true)
                putExtra(EXTRA_FORCE_SHOW, forceShow)
            }
            val canStartForeground =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    hasPostNotificationPermission(appContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && canStartForeground) {
                runCatching { ContextCompat.startForegroundService(appContext, intent) }
                    .onFailure { Log.e(TAG, "Unable to start overlay service", it) }
            } else {
                runCatching {
                    @Suppress("DEPRECATION")
                    appContext.startService(intent)
                }.onFailure { throwable ->
                    Log.e(TAG, "Unable to start overlay service in background", throwable)
                }
            }
        }

        fun setAllowedAppSuppressed(context: Context, suppressed: Boolean, reason: String = "allowed_app") {
            if (EmergencyUnlockState.isActive() || EmergencyUnlockStateStore.isActive(context)) {
                Log.d(TAG, "Skip allowed app suppression; emergency unlock active (reason=$reason)")
                return
            }
            val appContext = context.applicationContext
            val intent = Intent(appContext, OverlayLockService::class.java).apply {
                action = if (suppressed) ACTION_SUPPRESS_ALLOWED_APP else ACTION_RELEASE_ALLOWED_APP
                putExtra(EXTRA_START_REASON, reason)
                putExtra(EXTRA_REQUESTED_AT, System.currentTimeMillis())
                putExtra(EXTRA_DEBOUNCE_HANDLED, true)
            }
            val canStartForeground =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    hasPostNotificationPermission(appContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && canStartForeground) {
                runCatching { ContextCompat.startForegroundService(appContext, intent) }
                    .onFailure { Log.e(TAG, "Unable to update overlay suppression", it) }
            } else {
                runCatching {
                    @Suppress("DEPRECATION")
                    appContext.startService(intent)
                }.onFailure { throwable ->
                    Log.e(TAG, "Unable to update overlay suppression in background", throwable)
                }
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayLockService::class.java)
            context.stopService(intent)
        }

        private fun hasPostNotificationPermission(context: Context): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        }

        private fun shouldDebounce(reason: String?, bypass: Boolean): Boolean {
            val nowElapsed = SystemClock.elapsedRealtime()
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

    private data class ForegroundQueryResult(
        val packageName: String?,
        val fallbackToTimed: Boolean
    )

    private enum class OverlayMode {
        LOCK,
        PERMISSION_RECOVERY
    }

    private fun supportsDeviceProtectedStorage(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    private fun isUserUnlocked(): Boolean {
        if (!supportsDeviceProtectedStorage()) return true
        val userManager = getSystemService(UserManager::class.java)
        return userManager?.isUserUnlocked ?: true
    }
}
