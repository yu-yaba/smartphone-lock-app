package com.example.smartphone_lock.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LockColorScheme = darkColorScheme(
    primary = LockYellow,
    onPrimary = Color.Black,
    secondary = LockYellowDark,
    onSecondary = Color.Black,
    background = LockBackground,
    onBackground = LockOnBackground,
    surface = LockSurface,
    onSurface = LockOnBackground,
    surfaceVariant = LockSurface,
    onSurfaceVariant = LockOnBackground.copy(alpha = 0.8f)
)

@Composable
fun SmartphoneLockTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        window.statusBarColor = LockColorScheme.background.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }

    MaterialTheme(
        colorScheme = LockColorScheme,
        typography = Typography,
        content = content
    )
}
