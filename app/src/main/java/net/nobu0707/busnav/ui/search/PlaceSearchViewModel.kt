package net.nobu0707.busnav.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.nobu0707.busnav.search.NominatimPlaceSearchProvider

class PlaceSearchViewModel : ViewModel() {
    val holder = PlaceSearchStateHolder(NominatimPlaceSearchProvider(), viewModelScope)
}
