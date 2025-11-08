package com.example.smartphone_lock.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
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

    private var serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var lockStateJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile
    private var isLocked: Boolean = false
    private var foregroundStarted = false

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
        foregroundStarted = false
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        scheduleRestart()
    }

    private fun startMonitoring() {
        if (lockStateJob?.isActive != true) {
            lockStateJob = serviceScope.launch {
                lockRepository.lockState.collectLatest { state ->
                    isLocked = state.isLocked
                    if (state.isLocked) {
                        overlayManager.show()
                    }
                }
            }
        }
        foregroundAppMonitor.start(serviceScope) { packageName ->
            if (!isLocked) return@start
            if (lockRepository.shouldBlockPackage(packageName)) {
                Log.d(TAG, "Blocked package in foreground: $packageName")
                overlayManager.show()
            }
        }
    }

    @VisibleForTesting
    internal fun replaceServiceScope(testScope: CoroutineScope) {
        serviceScope.cancel()
        serviceScope = testScope
    }

    private fun ensureForeground() {
        if (foregroundStarted) return
        if (!enableForegroundNotificationsForTests) {
            foregroundStarted = true
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotificationPermission()) {
            Log.w(TAG, "Notification permission missing; defer foreground start")
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

    private fun scheduleRestart() {
        val intent = Intent(this, LockMonitorService::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getService(
            this,
            RESTART_REQUEST_CODE,
            intent,
            flags
        )
        val triggerAt = SystemClock.elapsedRealtime() + RESTART_DELAY_MILLIS
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarmManager?.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            pendingIntent
        )
    }

    companion object {
        private const val TAG = "LockMonitorService"
        private const val NOTIFICATION_ID = 2001
        private const val NOTIFICATION_CHANNEL_ID = "lock_monitor"
        private const val RESTART_REQUEST_CODE = 9001
        private const val RESTART_DELAY_MILLIS = 1_000L

        fun start(context: Context) {
            val intent = Intent(context, LockMonitorService::class.java)
            val canPostForegroundNotification = context.hasPostNotificationPermissionCompat()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && canPostForegroundNotification) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                @Suppress("DEPRECATION")
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LockMonitorService::class.java)
            context.stopService(intent)
        }

        @VisibleForTesting
        internal var enableForegroundNotificationsForTests: Boolean = true

        @VisibleForTesting
        internal fun startIntent(context: Context): Intent = Intent(context, LockMonitorService::class.java)
    }

    private fun hasPostNotificationPermission(): Boolean = this.hasPostNotificationPermissionCompat()
}

private fun Context.hasPostNotificationPermissionCompat(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}
