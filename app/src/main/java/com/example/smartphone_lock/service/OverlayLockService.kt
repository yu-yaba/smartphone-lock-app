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
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLockDisplay() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission missing; stopping service")
            stopLockDisplay()
            return
        }
        ensureForeground()
        startDeviceProtectedLockCollection()
        if (lockStateJob == null) {
            lockStateJob = serviceScope.launch {
                dataStoreManager.lockState.collectLatest { state ->
                    handleLockState(state)
                }
            }
        } else {
            latestLockState?.let { handleLockState(it) }
        }
    }

    private fun stopLockDisplay(clearState: Boolean = true) {
        countdownJob?.cancel()
        countdownJob = null
        lockStateJob?.cancel()
        lockStateJob = null
        deviceProtectedLockStateJob?.cancel()
        deviceProtectedLockStateJob = null
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
            return
        }
        if (deviceProtectedLockStateJob != null) return
        deviceProtectedLockStateJob = serviceScope.launch {
            dataStoreManager.deviceProtectedLockState.collectLatest { state ->
                if (isUserUnlocked()) {
                    cancel("User unlocked; DP overlay updates no longer needed")
                } else {
                    val snapshot = LockStatePreferences(
                        isLocked = state.isLocked,
                        lockStartTimestamp = state.lockStartTimestamp,
                        lockEndTimestamp = state.lockEndTimestamp
                    )
                    handleLockState(snapshot)
                }
            }
        }
    }

    private fun handleLockState(state: LockStatePreferences) {
        latestLockState = state
        if (state.isLocked && state.lockEndTimestamp != null) {
            ensureForeground()
            showOverlayIfNeeded()
            restartCountdown(state.lockEndTimestamp)
        } else {
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
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
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
        val container = FrameLayout(this).apply {
            setBackgroundColor(LOCK_BACKGROUND_COLOR)
            isClickable = true
            isFocusable = true
            setOnTouchListener { _, _ -> true }
        }
        val textView = TextView(this).apply {
            setTextColor(LOCK_TEXT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        container.addView(
            textView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
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

    companion object {
        private const val TAG = "OverlayLockService"
        private const val COUNTDOWN_INTERVAL_MILLIS = 1_000L
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "lock_overlay"
        private val LOCK_BACKGROUND_COLOR = Color.BLACK
        private val LOCK_TEXT_COLOR = Color.parseColor("#FFFFEB3B")

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
