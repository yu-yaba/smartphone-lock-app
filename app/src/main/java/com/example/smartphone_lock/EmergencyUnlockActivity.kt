package com.example.smartphone_lock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartphone_lock.service.EmergencyUnlockCoordinator
import com.example.smartphone_lock.ui.emergency.EmergencyUnlockViewModel
import com.example.smartphone_lock.ui.lock.LockScreenViewModel
import com.example.smartphone_lock.ui.screen.EmergencyUnlockScreen
import com.example.smartphone_lock.ui.theme.SmartphoneLockTheme
import dagger.hilt.android.AndroidEntryPoint
import com.example.smartphone_lock.service.LockMonitorService
import com.example.smartphone_lock.service.OverlayLockService

/**
 * オーバーレイの「緊急解除へ」から直接起動する専用アクティビティ。
 * NavHost 経由の初期ルート伝播に依存せず、確実に宣言文入力画面を表示する。
 */
@AndroidEntryPoint
class EmergencyUnlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EmergencyUnlockCoordinator.start()
        setContent {
            SmartphoneLockTheme {
                EmergencyUnlockContent(onFinished = { finish() })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 未解除で離脱した場合にオーバーレイを復帰させる
        LockMonitorService.start(this, reason = "emergency_exit", bypassDebounce = true)
        OverlayLockService.start(this, reason = "emergency_exit", bypassDebounce = true)
        EmergencyUnlockCoordinator.finish()
    }
}

@Composable
private fun EmergencyUnlockContent(onFinished: () -> Unit) {
    // Activity スコープの ViewModel を直接取得
    val lockViewModel: LockScreenViewModel = hiltViewModel()
    val emergencyUnlockViewModel: EmergencyUnlockViewModel = hiltViewModel()

    // 権限チェックやナビゲーションは不要。ロック状態に関わらず画面を開く。
    EmergencyUnlockScreen(
        lockViewModel = lockViewModel,
        emergencyUnlockViewModel = emergencyUnlockViewModel,
        onBackToLock = { onFinished() }
    )
}
