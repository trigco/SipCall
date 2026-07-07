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

// Glassmorphism inspired: cool blues and high translucency palette
private val GlassColors = lightColorScheme(
    primary            = Color(0xFF4A90E2),
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFD1E3F8),
    onPrimaryContainer = Color(0xFF003366),
    background         = Color(0xFFF0F4F8),
    onBackground       = Color(0xFF1A1C1E),
    surface            = Color(0xFFFFFFFF),
    onSurface          = Color(0xFF1A1C1E),
    surfaceVariant     = Color(0xFFE1E8ED),
    onSurfaceVariant   = Color(0xFF44474E),
    outline            = Color(0xFF74777F),
)

@Composable
fun IPDialTheme(
    themeMode: com.ipdial.data.model.ThemeMode = com.ipdial.data.model.ThemeMode.System,
    fontMultiplier: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val colors = when (themeMode) {
        com.ipdial.data.model.ThemeMode.Light -> LightColors
        com.ipdial.data.model.ThemeMode.Dark -> DarkColors
        com.ipdial.data.model.ThemeMode.Glass -> GlassColors
        else -> if (systemDark) DarkColors else LightColors
    }
    
    val scaledTypography = if (fontMultiplier != 1.0f) {
        Typography(
            displayLarge = IPDialTypography.displayLarge.copy(fontSize = IPDialTypography.displayLarge.fontSize * fontMultiplier),
            displayMedium = IPDialTypography.displayMedium.copy(fontSize = IPDialTypography.displayMedium.fontSize * fontMultiplier),
            headlineLarge = IPDialTypography.headlineLarge.copy(fontSize = IPDialTypography.headlineLarge.fontSize * fontMultiplier),
            headlineMedium = IPDialTypography.headlineMedium.copy(fontSize = IPDialTypography.headlineMedium.fontSize * fontMultiplier),
            titleLarge = IPDialTypography.titleLarge.copy(fontSize = IPDialTypography.titleLarge.fontSize * fontMultiplier),
            titleMedium = IPDialTypography.titleMedium.copy(fontSize = IPDialTypography.titleMedium.fontSize * fontMultiplier),
            bodyLarge = IPDialTypography.bodyLarge.copy(fontSize = IPDialTypography.bodyLarge.fontSize * fontMultiplier),
            bodyMedium = IPDialTypography.bodyMedium.copy(fontSize = IPDialTypography.bodyMedium.fontSize * fontMultiplier),
            labelLarge = IPDialTypography.labelLarge.copy(fontSize = IPDialTypography.labelLarge.fontSize * fontMultiplier),
            labelMedium = IPDialTypography.labelMedium.copy(fontSize = IPDialTypography.labelMedium.fontSize * fontMultiplier),
        )
    } else IPDialTypography

    MaterialTheme(
        colorScheme = colors,
        typography  = scaledTypography,
        shapes      = IPDialShapes,
        content     = content
    )
}
