package com.ipdial.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Palette ─────────────────────────────────────────────────────────────────
// Lifted from the phone app screenshots: muted sage background, deep forest green accents

val SageBackground   = Color(0xFFEAEFE9)  // Screen background
val ForestGreen      = Color(0xFF1E6B3C)  // Primary action (Call button)
val MintSurface      = Color(0xFFF2F7F1)  // Card/surface
val DarkForest       = Color(0xFF0D3D20)  // Primary container / on-primary
val EndRed           = Color(0xFFD32F2F)  // Hang up
val GrayText         = Color(0xFF5A6B5A)  // Secondary text
val OutlineGreen     = Color(0xFFB0C9B0)  // Borders
val OnSageText       = Color(0xFF1A2E1A)  // Primary text on sage bg

private val LightColors = lightColorScheme(
    primary            = ForestGreen,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFB7DFB9),
    onPrimaryContainer = DarkForest,
    secondary          = Color(0xFF4E7C54),
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFFCEEDD0),
    onSecondaryContainer = Color(0xFF0A3315),
    background         = SageBackground,
    onBackground       = OnSageText,
    surface            = MintSurface,
    onSurface          = OnSageText,
    surfaceVariant     = Color(0xFFDCE8DC),
    onSurfaceVariant   = GrayText,
    outline            = OutlineGreen,
    error              = EndRed,
    onError            = Color.White,
)

private val DarkColors = darkColorScheme(
    primary            = Color(0xFF8BCF8F),
    onPrimary          = Color(0xFF003912),
    primaryContainer   = Color(0xFF1E5228),
    onPrimaryContainer = Color(0xFFA7F0A8),
    background         = Color(0xFF000000),
    onBackground       = Color(0xFFE0E0E0),
    surface            = Color(0xFF000000),
    onSurface          = Color(0xFFE0E0E0),
    surfaceVariant     = Color(0xFF1E1E1E),
    onSurfaceVariant   = Color(0xFFB0C9B0),
    error              = Color(0xFFCF6679),
    onError            = Color(0xFF680022),
)

@Composable
fun IPDialTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography  = IPDialTypography,
        shapes      = IPDialShapes,
        content     = content
    )
}
