package com.kgcaudit.olocycle.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// OLO 밝은 테마 하나. 화면은 값이 아니라 역할 이름(OloColors)만 쓴다.
private val OloLightScheme = lightColorScheme(
    primary = OloColors.Primary,
    onPrimary = OloColors.OnPrimary,
    primaryContainer = OloColors.AccentContainer,
    onPrimaryContainer = OloColors.OnAccentContainer,
    secondary = OloColors.Primary,
    background = OloColors.Background,
    surface = OloColors.Surface,
    onBackground = OloColors.Ink,
    onSurface = OloColors.Ink,
    error = OloColors.Error,
    outline = OloColors.Outline,
)

@Composable
fun OloTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OloLightScheme, typography = Typography(), content = content)
}
