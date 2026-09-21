package net.nobu0707.busnav.ui.prescribed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.nobu0707.busnav.domain.prescribed.PrescribedRouteRepository

class PrescribedRouteLibraryViewModel(repository: PrescribedRouteRepository, activeId: () -> String?) : ViewModel() {
    val holder = PrescribedRouteLibraryStateHolder(repository, viewModelScope, activeId)
}