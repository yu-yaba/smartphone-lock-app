package jp.kawai.ultrafocus

import android.os.Bundle
import android.util.Log
import jp.kawai.ultrafocus.service.AllowedAppLauncherActivity
import jp.kawai.ultrafocus.data.repository.LockRepository
import jp.kawai.ultrafocus.emergency.EmergencyUnlockState
import jp.kawai.ultrafocus.navigation.AppDestination
import jp.kawai.ultrafocus.service.LockMonitorService
import jp.kawai.ultrafocus.service.OverlayLockService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import jp.kawai.ultrafocus.config.SupabaseConfigRepository
import jp.kawai.ultrafocus.data.datastore.LockStatePreferences
import jp.kawai.ultrafocus.ui.theme.UltraFocusTheme
import jp.kawai.ultrafocus.emergency.EmergencyUnlockStateStore
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    @JvmField
    var supabaseClient: SupabaseClient? = null

    @Inject
    lateinit var supabaseConfigRepository: SupabaseConfigRepository

    @Inject
    lateinit var lockRepository: LockRepository

    private var pendingNavRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNavRoute = intent?.getStringExtra(EXTRA_NAV_ROUTE)
        if (pendingNavRoute == AppDestination.EmergencyUnlock.route) {
            EmergencyUnlockState.setActive(true)
            EmergencyUnlockStateStore.setActive(this, true)
        }
        if (handleAllowedAppLaunch(intent)) {
            return
        }
        setContent {
            UltraFocusTheme {
                val lifecycleOwner = LocalLifecycleOwner.current
                val lockState by lockRepository.lockState.collectAsStateWithLifecycle(
                    initialValue = LockStatePreferences(false, null, null),
                    lifecycle = lifecycleOwner.lifecycle
                )
                val emergencyActive by EmergencyUnlockState.active.collectAsStateWithLifecycle(
                    initialValue = false,
                    lifecycle = lifecycleOwner.lifecycle
                )
                val now = System.currentTimeMillis()
                val locked = lockState.isLocked &&
                    (lockState.lockEndTimestamp?.let { it > now } != false)
                val showMainUi = shouldKeepMainUi(emergencyActive) || !locked
                val supabaseConfig = remember { supabaseConfigRepository.fetch() }
                if (BuildConfig.DEBUG) {
                    LaunchedEffect(supabaseConfig) {
                        if (supabaseConfig.url.isNullOrBlank() || supabaseConfig.anonKey.isNullOrBlank()) {
                            Log.i(TAG, "Supabase config missing; running without Supabase")
                        } else {
                            Log.d(TAG, "Supabase config present; ready to initialize client on demand")
                        }
                    }
                    LaunchedEffect(supabaseClient) {
                        supabaseClient?.let {
                            Log.d(TAG, "Supabase client ready: ${it.supabaseUrl}")
                        } ?: Log.i(TAG, "Supabase client not initialized (disabled)")
                    }
                }
                if (showMainUi) {
                    UltraFocusApp(
                        requestedNavRoute = pendingNavRoute,
                        onRequestedNavRouteConsumed = { pendingNavRoute = null }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (shouldKeepMainUi()) return
        lifecycleScope.launch {
            val state = lockRepository.lockState.first()
            val now = System.currentTimeMillis()
            val locked = state.isLocked && (state.lockEndTimestamp?.let { it > now } != false)
            if (locked) {
                OverlayLockService.setAllowedAppSuppressed(this@MainActivity, false, reason = "main_resume")
                OverlayLockService.start(
                    this@MainActivity,
                    reason = "main_resume",
                    bypassDebounce = true,
                    forceShow = true
                )
                LockMonitorService.start(this@MainActivity, reason = "main_resume", bypassDebounce = true)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        if (handleAllowedAppLaunch(intent)) {
            return
        }
        pendingNavRoute = intent.getStringExtra(EXTRA_NAV_ROUTE)
    }

    private fun handleAllowedAppLaunch(intent: android.content.Intent?): Boolean {
        val target = intent?.getStringExtra(EXTRA_ALLOWED_APP_TARGET) ?: return false
        val resolvedTarget = runCatching { AllowedAppLauncherActivity.Target.valueOf(target) }.getOrNull()
        if (resolvedTarget == null) {
            return false
        }
        AllowedAppLauncherActivity.start(this, resolvedTarget, null)
        finish()
        return true
    }

    private fun shouldKeepMainUi(): Boolean {
        return shouldKeepMainUi(EmergencyUnlockState.isActive())
    }

    private fun shouldKeepMainUi(emergencyActive: Boolean): Boolean {
        if (pendingNavRoute == AppDestination.EmergencyUnlock.route) {
            return true
        }
        return emergencyActive
    }

    companion object {
        const val EXTRA_NAV_ROUTE = "extra_nav_route"
        const val EXTRA_ALLOWED_APP_TARGET = "extra_allowed_app_target"
        private const val TAG = "MainActivity"
    }
}
