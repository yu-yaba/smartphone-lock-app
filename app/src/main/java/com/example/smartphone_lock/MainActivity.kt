package com.example.smartphone_lock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.smartphone_lock.data.repository.ConfigRepository
import dagger.hilt.android.AndroidEntryPoint
import com.example.smartphone_lock.ui.theme.SmartphoneLockTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var configRepository: ConfigRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // DI パイプラインの初期化が起動時に行われることを保証する。
        configRepository.getAppConfig()
        setContent {
            SmartphoneLockTheme {
                SmartphoneLockApp()
            }
        }
    }
}
