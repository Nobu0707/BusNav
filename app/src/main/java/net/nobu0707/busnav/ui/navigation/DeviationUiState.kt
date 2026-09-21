package net.nobu0707.busnav.ui.navigation

import net.nobu0707.busnav.domain.navigation.RouteDeviationSnapshot
import net.nobu0707.busnav.domain.navigation.RouteDeviationState
import net.nobu0707.busnav.domain.navigation.RouteMatchQuality

data class DeviationUiState(val message: String? = null, val isProminent: Boolean = false)

fun deviationUiState(snapshot: RouteDeviationSnapshot,
    mode: net.nobu0707.busnav.domain.prescribed.NavigationMode = net.nobu0707.busnav.domain.prescribed.NavigationMode.PRESCRIBED,
): DeviationUiState {
    val route = if (mode == net.nobu0707.busnav.domain.prescribed.NavigationMode.FREE) "案内経路" else "所定経路"
    return when {
    snapshot.matchQuality == RouteMatchQuality.UNRELIABLE && snapshot.state == RouteDeviationState.OFF_ROUTE ->
        DeviationUiState("${route}から外れている可能性があります・位置情報を確認中", true)
    snapshot.state == RouteDeviationState.ON_ROUTE -> DeviationUiState()
    snapshot.state == RouteDeviationState.SUSPECTED_OFF_ROUTE -> DeviationUiState("${route}との位置関係を確認中")
    snapshot.state == RouteDeviationState.OFF_ROUTE -> DeviationUiState("${route}から外れている可能性があります", true)
    snapshot.state == RouteDeviationState.RECOVERING -> DeviationUiState("${route}への復帰を確認中")
    else -> DeviationUiState("位置情報を確認中")
}

}
