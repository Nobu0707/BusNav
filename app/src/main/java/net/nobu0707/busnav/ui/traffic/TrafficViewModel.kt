package net.nobu0707.busnav.ui.traffic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.nobu0707.busnav.ui.navigation.NavigationStateHolder

class TrafficViewModel(navigation: NavigationStateHolder) : ViewModel() {
    val holder = TrafficStateHolder(createTrafficProvider(), navigation, viewModelScope,
        diagnostics = { if (net.nobu0707.busnav.BuildConfig.DEBUG) android.util.Log.d("BusNavTraffic", it) })
}
