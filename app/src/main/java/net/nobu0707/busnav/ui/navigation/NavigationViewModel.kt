package net.nobu0707.busnav.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.nobu0707.busnav.domain.route.ScheduledRouteRepository
import net.nobu0707.busnav.location.LocationProvider

/** Keeps the applied route and its matching guidance across Activity recreation. */
class NavigationViewModel(locationProvider: LocationProvider, routeRepository: ScheduledRouteRepository) : ViewModel() {
    val stateHolder = NavigationStateHolder(locationProvider, routeRepository, viewModelScope,
        elapsedMillis = { android.os.SystemClock.elapsedRealtime() },
        diagnostics = { if (net.nobu0707.busnav.BuildConfig.DEBUG) android.util.Log.d("BusNavNavigation", it) })
}
