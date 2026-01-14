package jp.kawai.ultrafocus.ui.theme

import androidx.compose.ui.graphics.Color

// Core palette (3 colors + alpha variants)
val PrimaryBlue = Color(0xFF0516FF)
val NeutralWhite = Color(0xFFFFFFFF)
val NeutralBlack = Color(0xFF000000)

// Derived tones (alpha only)
val PrimaryBlueMuted = PrimaryBlue.copy(alpha = 0.6f)
val TextBlackMuted = NeutralBlack.copy(alpha = 0.6f)
val OutlineBlack = NeutralBlack.copy(alpha = 0.12f)
val ScrimBlack = NeutralBlack.copy(alpha = 0.45f)

// Surfaces / Text
val BackgroundWhite = NeutralWhite
val SurfaceWhite = NeutralWhite
val TextBlack = NeutralBlack
val TextOnPrimary = NeutralWhite

// Effects
val GlassSurface = NeutralWhite.copy(alpha = 0.92f)
val GlassSurfaceTint = PrimaryBlue.copy(alpha = 0.12f)
val GlassBorder = NeutralWhite.copy(alpha = 0.55f)

// Gradients
val GradientPrimaryStart = PrimaryBlue.copy(alpha = 0.12f)
val GradientPrimaryEnd = PrimaryBlue
