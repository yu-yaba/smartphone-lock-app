package com.example.smartphone_lock

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.example.smartphone_lock.config.SupabaseConfigRepository
import dagger.hilt.android.AndroidEntryPoint
import com.example.smartphone_lock.ui.theme.SmartphoneLockTheme
import io.github.jan.supabase.SupabaseClient
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var supabaseClient: SupabaseClient

    @Inject
    lateinit var supabaseConfigRepository: SupabaseConfigRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // DI パイプラインの初期化が起動時に行われることを保証する。
        configRepository.getAppConfig()
        setContent {
            SmartphoneLockTheme {
                val supabaseConfig = remember { supabaseConfigRepository.fetch() }
                if (BuildConfig.DEBUG) {
                    LaunchedEffect(supabaseConfig) {
                        check(supabaseConfig.url != null && supabaseConfig.anonKey != null) {
                            "Supabase config is missing. Set SUPABASE_URL and SUPABASE_ANON_KEY in local.properties."
                        }
                    }
                    LaunchedEffect(supabaseClient) {
                        Log.d(TAG, "Supabase client ready: ${supabaseClient.supabaseUrl}")
                    }
                }
                SmartphoneLockApp()
            }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
