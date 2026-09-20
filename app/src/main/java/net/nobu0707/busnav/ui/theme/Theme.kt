package net.nobu0707.busnav.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val BusNavDarkColors = darkColorScheme(
    primary = NightPrimary, onPrimary = NightOnPrimary,
    background = NightBackground, onBackground = NightText,
    surface = NightSurface, onSurface = NightText,
    surfaceVariant = NightSurfaceRaised, onSurfaceVariant = NightMutedText,
    error = AlertRed,
)
private val BusNavLightColors = lightColorScheme(
    primary = Color(0xFF006782), onPrimary = Color.White,
    background = Color(0xFFF6F8FA), onBackground = Color(0xFF19252E),
    surface = Color(0xFFF6F8FA), onSurface = Color(0xFF19252E),
    surfaceVariant = Color(0xFFE0E8ED), onSurfaceVariant = Color(0xFF43545F),
)

@Composable
fun BusNavTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (darkTheme) BusNavDarkColors else BusNavLightColors
    MaterialTheme(colorScheme = colors) {
        // MaterialTheme alone does not set the color inherited by bare Text.
        CompositionLocalProvider(LocalContentColor provides colors.onSurface, content = content)
    }
}
