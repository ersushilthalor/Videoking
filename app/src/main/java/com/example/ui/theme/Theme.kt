package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CinemaGold,
    onPrimary = Color.Black,
    secondary = CinemaDarkGrey,
    onSecondary = Color.White,
    tertiary = CinemaAccentRed,
    background = Color.Black,
    surface = CinemaSurface,
    onSurface = CinemaLightText,
    surfaceVariant = CinemaDarkGrey,
    onSurfaceVariant = CinemaMutedText
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
