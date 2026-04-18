package com.lazycontainer.compose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B5BFE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3FF),
    onPrimaryContainer = Color(0xFF0A1C6B),
    background = Color(0xFFF4F5F7),
    onBackground = Color(0xFF1B1C1F),
    surface = Color.White,
    onSurface = Color(0xFF1B1C1F),
    surfaceVariant = Color(0xFFE8EAF0),
    onSurfaceVariant = Color(0xFF5A5D66),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB3C0FF),
    onPrimary = Color(0xFF11227A),
    primaryContainer = Color(0xFF27388F),
    onPrimaryContainer = Color(0xFFDDE3FF),
    background = Color(0xFF121317),
    onBackground = Color(0xFFE4E5E9),
    surface = Color(0xFF1B1C20),
    onSurface = Color(0xFFE4E5E9),
    surfaceVariant = Color(0xFF2A2B31),
    onSurfaceVariant = Color(0xFFB5B7BE),
)

@Composable
fun LazyContainerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
