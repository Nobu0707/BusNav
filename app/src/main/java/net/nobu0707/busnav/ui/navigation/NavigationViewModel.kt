package net.nobu0707.busnav.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.nobu0707.busnav.domain.route.ScheduledRouteRepository
import net.nobu0707.busnav.location.LocationProvider
import net.nobu0707.busnav.domain.prescribed.PrescribedRouteRepository
import net.nobu0707.busnav.data.facility.HttpRouteFacilityProvider
import net.nobu0707.busnav.ui.facility.RouteFacilityStateHolder

/** Keeps the applied route and its matching guidance across Activity recreation. */
class NavigationViewModel(locationProvider: LocationProvider, routeRepository: ScheduledRouteRepository,
    prescribedRepository: PrescribedRouteRepository? = null) : ViewModel() {
    val facilities = RouteFacilityStateHolder(HttpRouteFacilityProvider(), viewModelScope, prescribedRepository)
    var camera: net.nobu0707.busnav.ui.routeplan.EditorCamera? = null
    var hasNavigationCamera: Boolean = false
    val stateHolder = NavigationStateHolder(locationProvider, routeRepository, viewModelScope,
        elapsedMillis = { android.os.SystemClock.elapsedRealtime() },
        diagnostics = { if (net.nobu0707.busnav.BuildConfig.DEBUG) android.util.Log.d("BusNavNavigation", it) })
}
