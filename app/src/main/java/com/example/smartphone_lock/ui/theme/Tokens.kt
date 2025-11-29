package com.example.smartphone_lock.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 4dp ベースの余白トークン。
 */
data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp
)

/**
 * 角丸トークン。
 */
data class Radius(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val pill: Dp = 999.dp
)

/**
 * 影・奥行きトークン。Material3 の elevation にマップして利用する。
 */
data class Elevations(
    val level1: Dp = 6.dp,
    val level2: Dp = 12.dp,
    val level3: Dp = 16.dp
)

data class LockGradients(
    val skyDawn: Brush = Brush.verticalGradient(
        colors = listOf(GradientSkyStart, GradientSkyEnd)
    )
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalRadius = staticCompositionLocalOf { Radius() }
val LocalElevations = staticCompositionLocalOf { Elevations() }
val LocalGradients = staticCompositionLocalOf { LockGradients() }
