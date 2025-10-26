package com.example.smartphone_lock.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AuthScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToLockSetting: () -> Unit,
    onNavigateToComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaceholderScreen(
        title = "Auth",
        description = "認証の仮画面です。フロー確認用のボタンを配置しています。",
        actions = listOf(
            "Home 画面へ" to onNavigateToHome,
            "LockSetting 画面へ" to onNavigateToLockSetting,
            "Complete 画面へ" to onNavigateToComplete
        ),
        modifier = modifier
    )
}
