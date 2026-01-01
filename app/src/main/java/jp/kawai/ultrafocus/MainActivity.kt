package jp.kawai.ultrafocus

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import jp.kawai.ultrafocus.config.SupabaseConfigRepository
import jp.kawai.ultrafocus.ui.theme.UltraFocusTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    @JvmField
    var supabaseClient: SupabaseClient? = null

    @Inject
    lateinit var supabaseConfigRepository: SupabaseConfigRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UltraFocusTheme {
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
                UltraFocusApp()
            }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
