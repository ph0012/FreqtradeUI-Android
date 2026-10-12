package com.freqtradeui.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Esquema de cores do aplicativo. Apenas tema escuro é fornecido nesta etapa,
 * suficiente para uma interface com aparência de terminal financeiro.
 */
private val FreqtradeColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = MidnightNavy,
    primaryContainer = DeepNavy,
    onPrimaryContainer = CyanAccent,
    secondary = ProfitGreen,
    onSecondary = MidnightNavy,
    tertiary = AmberWarn,
    onTertiary = MidnightNavy,
    background = MidnightNavy,
    onBackground = TextPrimary,
    surface = DeepNavy,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSoft,
    error = LossRed,
    onError = MidnightNavy,
)

@Composable
fun FreqtradeUITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FreqtradeColorScheme,
        typography = FreqtradeTypography,
        content = content,
    )
}
