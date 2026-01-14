package jp.kawai.ultrafocus.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
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
        colors = listOf(GradientPrimaryEnd, GradientPrimaryStart) // 上:青 → 下:淡い青
    )
)

/**
 * Liquid Glass 風のサーフェス設定。
 */
data class GlassStyle(
    val background: Color = GlassSurface,
    val tint: Color = GlassSurfaceTint,
    val border: Color = GlassBorder,
    val blurRadius: Dp = 0.dp, // Compose の backdrop blur が未サポートのためデフォルト 0。将来置換を想定。
    val highlight: Brush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.35f),
            Color.White.copy(alpha = 0.10f)
        ),
        start = Offset(0f, 0f),
        end = Offset(0f, 420f)
    )
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalRadius = staticCompositionLocalOf { Radius() }
val LocalElevations = staticCompositionLocalOf { Elevations() }
val LocalGradients = staticCompositionLocalOf { LockGradients() }
val LocalGlass = staticCompositionLocalOf { GlassStyle() }
