package com.kgcaudit.olocycle.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightScheme = lightColorScheme(
    primary = OloColors.Primary,
    onPrimary = OloColors.OnPrimary,
    secondary = OloColors.Accent,
    background = OloColors.Background,
    surface = OloColors.Surface,
    onBackground = OloColors.Ink,
    onSurface = OloColors.Ink,
)

@Composable
fun OloTheme(content: @Composable () -> Unit) {
    // A single warm light scheme for now; a dark scheme is planned.
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    MaterialTheme(colorScheme = LightScheme, typography = Typography(), content = content)
}
