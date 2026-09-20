package net.nobu0707.busnav.ui.theme

import androidx.compose.runtime.*
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.nobu0707.busnav.domain.model.GeoPoint

@Composable
fun rememberIsNight(point: GeoPoint?, clock: java.time.Clock? = null): Boolean {
    val cache = remember { SolarDayCache() }
    var night by remember(point, clock) { mutableStateOf(cache.isNight(point, clock?.instant() ?: Instant.now(), clock?.zone ?: ZoneId.systemDefault())) }
    LaunchedEffect(point, clock) {
        while (isActive) {
            night = cache.isNight(point, clock?.instant() ?: Instant.now(), clock?.zone ?: ZoneId.systemDefault())
            delay(1_000)
        }
    }
    return night
}