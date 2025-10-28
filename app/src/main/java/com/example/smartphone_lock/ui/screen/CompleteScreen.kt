package com.example.smartphone_lock.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun CompleteScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToLockSetting: () -> Unit,
    onNavigateToAuth: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaceholderScreen(
        title = "Complete",
        description = "設定完了の仮画面です。他の画面に戻ることができます。",
        actions = listOf(
            "Home 画面へ" to onNavigateToHome,
            "LockSetting 画面へ" to onNavigateToLockSetting,
            "Auth 画面へ" to onNavigateToAuth
        ),
        modifier = modifier
    )
}
