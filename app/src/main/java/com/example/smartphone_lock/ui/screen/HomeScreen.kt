package com.example.smartphone_lock.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HomeScreen(
    onNavigateToLockSetting: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaceholderScreen(
        title = "Home",
        description = "仮のホーム画面です。ナビゲーションの動作を確認できます。",
        actions = listOf(
            "LockSetting 画面へ" to onNavigateToLockSetting,
            "Auth 画面へ" to onNavigateToAuth,
            "Complete 画面へ" to onNavigateToComplete
        ),
        modifier = modifier
    )
}
