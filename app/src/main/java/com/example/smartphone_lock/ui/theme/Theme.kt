package com.example.smartphone_lock.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp

private val LockColorScheme = lightColorScheme(
    primary = PrimarySky,
    onPrimary = TextOnPrimary,
    secondary = PrimarySkyLight,
    onSecondary = TextOnPrimary,
    background = BackgroundSky,
    onBackground = TextPrimary,
    surface = SurfaceBase,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceBase,
    onSurfaceVariant = TextSecondary,
    outline = OutlineHairline,
    error = WarningRed,
    onError = TextOnPrimary
)

private val LockShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
)

@Composable
fun SmartphoneLockTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        window.statusBarColor = Color.Transparent.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
    }

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalRadius provides Radius(),
        LocalElevations provides Elevations(),
        LocalGradients provides LockGradients(),
        LocalGlass provides GlassStyle()
    ) {
        MaterialTheme(
            colorScheme = LockColorScheme,
            typography = Typography,
            shapes = LockShapes,
            content = content
        )
    }
}

// Convenience accessors
val MaterialTheme.spacing: Spacing
    @Composable
    get() = LocalSpacing.current

val MaterialTheme.radius: Radius
    @Composable
    get() = LocalRadius.current

val MaterialTheme.elevations: Elevations
    @Composable
    get() = LocalElevations.current

val MaterialTheme.gradients: LockGradients
    @Composable
    get() = LocalGradients.current

val MaterialTheme.glass: GlassStyle
    @Composable
    get() = LocalGlass.current
