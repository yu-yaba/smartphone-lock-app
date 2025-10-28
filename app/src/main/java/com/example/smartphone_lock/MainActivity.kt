package com.example.smartphone_lock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.example.smartphone_lock.config.SupabaseConfigRepository
import dagger.hilt.android.AndroidEntryPoint
import com.example.smartphone_lock.ui.theme.SmartphoneLockTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val supabaseConfigRepository = SupabaseConfigRepository()
        setContent {
            SmartphoneLockTheme {
                val supabaseConfig = remember { supabaseConfigRepository.fetch() }
                if (BuildConfig.DEBUG) {
                    LaunchedEffect(supabaseConfig) {
                        check(supabaseConfig.url != null && supabaseConfig.anonKey != null) {
                            "Supabase config is missing. Set SUPABASE_URL and SUPABASE_ANON_KEY in local.properties."
                        }
                    }
                }
                SmartphoneLockApp()
            }
        }
    }
}
