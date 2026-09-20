package net.nobu0707.busnav.map.basemap

/**
 * Owns detailed-style/fallback transitions without depending on MapLibre so the
 * failure path stays deterministic and unit-testable.
 */
class BasemapController(
    private var config: BasemapConfig,
    private val diagnostics: MapDiagnostics,
    private val onStateChanged: (BasemapState) -> Unit,
) {
    var state: BasemapState = if (config.mode == BasemapMode.FALLBACK) {
        BasemapState.UNAVAILABLE
    } else {
        BasemapState.LOADING
    }
        private set

    private var fallbackActive = config.mode == BasemapMode.FALLBACK
    private var detailedStyleLoaded = false

    val isFallbackActive: Boolean
        get() = fallbackActive

    fun updateConfig(newConfig: BasemapConfig): String? {
        if (config == newConfig) return null
        config = newConfig
        fallbackActive = config.mode == BasemapMode.FALLBACK
        detailedStyleLoaded = false
        return initialStyle()
    }

    fun initialStyle(): String {
        val resource = config.styleUrl ?: config.fallbackStyleUrl
        if (fallbackActive) {
            updateState(BasemapState.UNAVAILABLE)
        } else {
            diagnostics.record(MapDiagnosticEvent.STYLE_LOAD_START, resource)
            updateState(BasemapState.LOADING)
        }
        return resource
    }

    fun onStyleLoaded() {
        if (fallbackActive) {
            updateState(BasemapState.UNAVAILABLE)
            return
        }
        detailedStyleLoaded = true
        diagnostics.record(MapDiagnosticEvent.STYLE_LOAD_SUCCESS, config.styleUrl)
        updateState(BasemapState.AVAILABLE)
    }

    /**
     * Returns the embedded fallback style when a detailed style failed before it
     * completed. Tile/source failures after a successful style load do not cause
     * a destructive style reload.
     */
    fun onMapLoadFailed(detail: String): String? {
        if (!fallbackActive && !detailedStyleLoaded) {
            diagnostics.record(MapDiagnosticEvent.STYLE_LOAD_FAILED, config.styleUrl, detail)
            fallbackActive = true
            updateState(BasemapState.UNAVAILABLE)
            return config.fallbackStyleUrl
        }
        diagnostics.record(MapDiagnosticEvent.SOURCE_ERROR, config.styleUrl, detail)
        updateState(BasemapState.UNAVAILABLE)
        return null
    }

    private fun updateState(newState: BasemapState) {
        if (state != newState) {
            state = newState
        }
        onStateChanged(state)
    }
}
