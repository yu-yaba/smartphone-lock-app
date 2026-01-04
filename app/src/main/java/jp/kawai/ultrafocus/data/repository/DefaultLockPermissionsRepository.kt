package jp.kawai.ultrafocus.data.repository

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import jp.kawai.ultrafocus.model.LockPermissionState
import jp.kawai.ultrafocus.util.canUseExactAlarms
import jp.kawai.ultrafocus.util.requestExactAlarmIntent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Overlay / UsageStats の状態を監視する実装。
 * 設定画面から復帰したタイミングで ProcessLifecycleOwner から再評価を行い、
 * UI に最新の状態を通知する。
 */
@Singleton
class DefaultLockPermissionsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : LockPermissionsRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val state = MutableStateFlow(readCurrentState())

    override val permissionStateFlow: Flow<LockPermissionState> = state.asStateFlow()

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            scope.launch {
                emitLatestState()
            }
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        startAppOpsWatchers()
    }

    override suspend fun refreshPermissionState() {
        emitLatestState()
    }

    private suspend fun emitLatestState() {
        state.emit(readCurrentState())
    }

    private fun readCurrentState(): LockPermissionState {
        return LockPermissionState(
            overlayGranted = Settings.canDrawOverlays(context),
            usageStatsGranted = isUsageStatsGranted(context),
            exactAlarmGranted = context.canUseExactAlarms(),
            notificationGranted = isNotificationGranted(context)
        )
    }

    private fun isUsageStatsGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_FOREGROUND
    }

    private fun isNotificationGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "LockPermissionsRepository"

        fun overlaySettingsIntent(context: Context): Intent {
            return Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        }

        fun usageAccessSettingsIntent(): Intent {
            return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        }

        fun exactAlarmSettingsIntent(context: Context): Intent {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.requestExactAlarmIntent()
            } else {
                appDetailsSettingsIntent(context)
            }
        }

        fun appDetailsSettingsIntent(context: Context): Intent {
            return Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        }
    }

    private fun startAppOpsWatchers() {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return
        val listener = AppOpsManager.OnOpChangedListener { op, pkg ->
            if (pkg != context.packageName) return@OnOpChangedListener
            if (op == AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW || op == AppOpsManager.OPSTR_GET_USAGE_STATS) {
                Log.i(TAG, "AppOp changed for $op; re-evaluating permissions")
                scope.launch { emitLatestState() }
            }
        }
        // register watchers for both Overlay and Usage access
        appOps.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, context.packageName, listener)
        appOps.startWatchingMode(AppOpsManager.OPSTR_GET_USAGE_STATS, context.packageName, listener)
    }
}
