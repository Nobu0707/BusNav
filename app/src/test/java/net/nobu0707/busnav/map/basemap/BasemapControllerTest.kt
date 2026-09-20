package net.nobu0707.busnav.map.basemap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BasemapControllerTest {
    private val config = BasemapConfig(
        styleUrl = "http://10.0.2.2:8080/styles/busnav/style.json",
        mode = BasemapMode.LOCAL_DEV,
    )

    @Test
    fun detailedStyleTransitionsFromLoadingToAvailable() {
        val states = mutableListOf<BasemapState>()
        val diagnostics = RecordingMapDiagnostics()
        val controller = BasemapController(config, diagnostics, states::add)

        assertEquals(config.styleUrl, controller.initialStyle())
        controller.onStyleLoaded()

        assertEquals(BasemapState.AVAILABLE, controller.state)
        assertEquals(listOf(BasemapState.LOADING, BasemapState.AVAILABLE), states)
        assertEquals(
            listOf(MapDiagnosticEvent.STYLE_LOAD_START, MapDiagnosticEvent.STYLE_LOAD_SUCCESS),
            diagnostics.events,
        )
    }

    @Test
    fun detailedStyleFailureRequestsFallbackAndStaysUnavailable() {
        val controller = BasemapController(config, NoOpMapDiagnostics) {}
        controller.initialStyle()

        assertEquals(BasemapConfig.FALLBACK_STYLE_URL, controller.onMapLoadFailed("connection refused"))
        controller.onStyleLoaded()

        assertEquals(BasemapState.UNAVAILABLE, controller.state)
        assertNull(controller.onMapLoadFailed("fallback failed"))
    }

    @Test
    fun sourceFailureAfterSuccessfulLoadDoesNotReloadStyle() {
        val diagnostics = RecordingMapDiagnostics()
        val controller = BasemapController(config, diagnostics) {}
        controller.initialStyle()
        controller.onStyleLoaded()

        assertNull(controller.onMapLoadFailed("tile request failed"))
        assertEquals(BasemapState.UNAVAILABLE, controller.state)
        assertEquals(MapDiagnosticEvent.SOURCE_ERROR, diagnostics.events.last())
    }

    private class RecordingMapDiagnostics : MapDiagnostics {
        val events = mutableListOf<MapDiagnosticEvent>()

        override fun record(event: MapDiagnosticEvent, resource: String?, detail: String?) {
            events += event
        }
    }
}
