package com.example.smartphone_lock.service

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
import android.os.UserManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.smartphone_lock.BuildConfig
import com.example.smartphone_lock.R
import com.example.smartphone_lock.data.datastore.DataStoreManager
import com.example.smartphone_lock.data.datastore.LockStatePreferences
import com.example.smartphone_lock.util.formatLockRemainingTime
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

@AndroidEntryPoint
class OverlayLockService : Service() {

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var overlayContainer: FrameLayout? = null
    private var countdownTextView: TextView? = null
    private var lockStateJob: Job? = null
    private var deviceProtectedLockStateJob: Job? = null
    private var countdownJob: Job? = null
    private var userUnlockWatcherJob: Job? = null
    private var deviceProtectedSnapshot: LockStatePreferences? = null
    private var foregroundStarted = false
    private var latestLockState: LockStatePreferences? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, null -> startLockDisplay()
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
        ensureForeground()
        startDeviceProtectedLockCollection()
        startCredentialEncryptedLockCollectionIfUnlocked()
        latestLockState?.let { handleLockState(it) }
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
        latestLockState = null
        hideOverlay()
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
                    Log.e(TAG, "Credential-encrypted lock state unavailable", throwable)
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
            showOverlayIfNeeded()
            restartCountdown(effectiveState.lockEndTimestamp)
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
            val gradientDrawable = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(COLOR_GRADIENT_END, COLOR_GRADIENT_START)
            )
            background = gradientDrawable
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
            textView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8f, metrics)
                bottomMargin = dpToPx(8f, metrics)
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
        countdownTextView = textView
        windowManager.addView(container, layoutParams)
    }

    private fun hideOverlay() {
        countdownTextView = null
        overlayContainer?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayContainer = null
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

    private fun dpToPx(value: Float, metrics: DisplayMetrics): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics).toInt()

    companion object {
        private const val TAG = "OverlayLockService"
        private const val COUNTDOWN_INTERVAL_MILLIS = 1_000L
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "lock_overlay"
        private const val RESTART_REQUEST_CODE = 9002

        // Sky Concept Colors (aligned with concept.md)
        private val COLOR_GRADIENT_START = Color.parseColor("#E6F2FF")
        private val COLOR_GRADIENT_END = Color.parseColor("#0A84FF")
        private val COLOR_TEXT_DARK_NAVY = Color.parseColor("#0B1A2D")
        private val COLOR_TEXT_SECONDARY = Color.parseColor("#8291A8")
        private val COLOR_WARNING_RED = Color.parseColor("#FF3B30")
        private val COLOR_CLEAN_WHITE = Color.WHITE

        const val ACTION_START = "com.example.smartphone_lock.action.START_LOCK"

        fun start(context: Context) {
            val intent = Intent(context, OverlayLockService::class.java).apply {
                action = ACTION_START
            }
            val canStartForeground =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    hasPostNotificationPermission(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && canStartForeground) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                try {
                    @Suppress("DEPRECATION")
                    context.startService(intent)
                } catch (illegalStateException: IllegalStateException) {
                    Log.e(TAG, "Unable to start overlay service in background", illegalStateException)
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
    }

    private fun supportsDeviceProtectedStorage(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    private fun isUserUnlocked(): Boolean {
        if (!supportsDeviceProtectedStorage()) return true
        val userManager = getSystemService(UserManager::class.java)
        return userManager?.isUserUnlocked ?: true
    }
}
