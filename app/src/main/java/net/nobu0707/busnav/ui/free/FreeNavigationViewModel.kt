package net.nobu0707.busnav.ui.free

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.nobu0707.busnav.domain.routing.RoutingEngine
import net.nobu0707.busnav.ui.navigation.NavigationStateHolder

class FreeNavigationViewModel(engine: RoutingEngine, navigation: NavigationStateHolder) : ViewModel() {
    val holder = FreeNavigationStateHolder(engine, navigation, viewModelScope)
}
