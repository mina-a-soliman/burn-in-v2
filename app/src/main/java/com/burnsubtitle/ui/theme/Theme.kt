package com.burnsubtitle.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = NavyDark,
    secondary = Navy,
    background = SurfaceDark,
    surface = SurfaceDark,
    surfaceContainer = SurfaceContainer,
    onBackground = OnSurface,
    onSurface = OnSurface,
    onSurfaceVariant = Outline,
    outline = Outline,
)

@Composable
fun BurnSubtitleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = Typography,
        content = content,
    )
}
