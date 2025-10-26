package com.example.smartphone_lock.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun LockSettingScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaceholderScreen(
        title = "Lock Setting",
        description = "ロック設定の仮画面です。設定後の遷移を確認できます。",
        actions = listOf(
            "Home 画面へ" to onNavigateToHome,
            "Auth 画面へ" to onNavigateToAuth,
            "Complete 画面へ" to onNavigateToComplete
        ),
        modifier = modifier
    )
}
