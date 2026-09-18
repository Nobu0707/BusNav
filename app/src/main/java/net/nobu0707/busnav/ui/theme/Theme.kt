package net.nobu0707.busnav.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BusNavDarkColors = darkColorScheme(
    primary = NightPrimary,
    onPrimary = NightOnPrimary,
    background = NightBackground,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightSurfaceRaised,
    onSurfaceVariant = NightMutedText,
    error = AlertRed,
)

@Composable
fun BusNavTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BusNavDarkColors,
        content = content,
    )
}
