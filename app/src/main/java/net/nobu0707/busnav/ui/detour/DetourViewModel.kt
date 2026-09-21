package net.nobu0707.busnav.ui.detour

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.nobu0707.busnav.domain.routing.RoutingEngine
import net.nobu0707.busnav.ui.navigation.NavigationStateHolder

class DetourViewModel(engine: RoutingEngine, navigation: NavigationStateHolder) : ViewModel() {
    val holder = DetourStateHolder(engine, navigation, viewModelScope, { android.os.SystemClock.elapsedRealtime() },
        diagnostics = { if (net.nobu0707.busnav.BuildConfig.DEBUG) android.util.Log.d("BusNavDetour", it) })
}
