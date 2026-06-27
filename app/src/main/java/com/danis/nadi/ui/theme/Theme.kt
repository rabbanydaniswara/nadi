package com.danis.nadi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = NadiGreen,
    onPrimary = NadiSurface,
    primaryContainer = NadiGreenLight,
    onPrimaryContainer = NadiGreenDark,
    background = NadiBackground,
    onBackground = NadiTextPrimary,
    surface = NadiSurface,
    onSurface = NadiTextPrimary,
    error = NadiError,
    outline = NadiBorder
)

@Composable
fun NadiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
