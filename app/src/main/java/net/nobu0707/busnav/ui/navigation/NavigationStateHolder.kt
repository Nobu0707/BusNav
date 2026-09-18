package net.nobu0707.busnav.ui.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import net.nobu0707.busnav.location.LocationProvider
import net.nobu0707.busnav.location.LocationUpdate

class NavigationStateHolder(
    private val locationProvider: LocationProvider,
    private val scope: CoroutineScope,
) {
    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

    private var locationJob: Job? = null

    fun setLayoutMode(layoutMode: NavigationLayoutMode) = update { copy(layoutMode = layoutMode) }

    fun setPermission(permissionState: LocationPermissionState) {
        update { copy(locationPermissionState = permissionState, isLoading = false) }
        if (permissionState == LocationPermissionState.Granted) startLocationUpdates() else stopLocationUpdates()
    }

    fun startLocationUpdates() {
        if (_uiState.value.locationPermissionState != LocationPermissionState.Granted) return
        if (locationJob?.isActive == true) return
        if (!locationProvider.isLocationEnabled()) {
            update { copy(locationError = "端末の位置情報を有効にしてください", isLoading = false) }
        }
        locationJob = scope.launch {
            locationProvider.updates()
                .catch { error ->
                    update { copy(locationError = error.message ?: "位置情報を取得できませんでした", isLoading = false) }
                }
                .collect { locationUpdate ->
                    when (locationUpdate) {
                        is LocationUpdate.Position -> update {
                            copy(location = locationUpdate.location, locationError = null, isLoading = false)
                        }
                        is LocationUpdate.Error -> update {
                            copy(locationError = locationUpdate.message, isLoading = false)
                        }
                        LocationUpdate.Disabled -> update {
                            copy(locationError = "端末の位置情報を有効にしてください", isLoading = false)
                        }
                    }
                }
        }
    }

    fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
    }

    fun onMapReady() = update { copy(isMapReady = true) }

    fun onMapError(message: String) = update { copy(isMapReady = false, locationError = message, isLoading = false) }

    fun onManualMapGesture() = update { copy(isFollowingLocation = false) }

    fun onCurrentLocationRequested() = update {
        copy(isFollowingLocation = true, recenterRequestId = recenterRequestId + 1)
    }

    private inline fun update(transform: NavigationUiState.() -> NavigationUiState) {
        _uiState.value = _uiState.value.transform()
    }
}

