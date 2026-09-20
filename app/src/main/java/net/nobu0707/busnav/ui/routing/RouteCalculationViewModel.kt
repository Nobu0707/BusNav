package net.nobu0707.busnav.ui.routing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.nobu0707.busnav.domain.routing.RoutingEngine

class RouteCalculationViewModel(engine: RoutingEngine) : ViewModel() {
    val stateHolder = RouteCalculationStateHolder(engine, viewModelScope)
}
