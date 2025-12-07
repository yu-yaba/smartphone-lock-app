package com.example.smartphone_lock

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.smartphone_lock.config.SupabaseConfigRepository
import com.example.smartphone_lock.ui.theme.SmartphoneLockTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val initialRouteState = mutableStateOf<InitialRoute?>(null)

    @Inject
    @JvmField
    var supabaseClient: SupabaseClient? = null

    @Inject
    lateinit var supabaseConfigRepository: SupabaseConfigRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialRouteState.value = InitialRoute(intent?.getStringExtra(EXTRA_NAV_ROUTE))
        setContent {
            SmartphoneLockTheme {
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
                SmartphoneLockApp(initialRoute = initialRouteState.value)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialRouteState.value = InitialRoute(intent?.getStringExtra(EXTRA_NAV_ROUTE))
    }

    companion object {
        const val EXTRA_NAV_ROUTE = "extra_nav_route"
        const val TAG = "MainActivity"
    }
}

data class InitialRoute(
    val route: String?,
    val nonce: Long = SystemClock.uptimeMillis()
)
