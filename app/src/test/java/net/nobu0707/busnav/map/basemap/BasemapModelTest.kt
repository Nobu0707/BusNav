package net.nobu0707.busnav.map.basemap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BasemapModelTest {
    @Test
    fun attributionCreditsBothDataAndSchema() {
        assertTrue(BasemapAttribution.VISIBLE_TEXT.contains("OpenStreetMap contributors"))
        assertTrue(BasemapAttribution.VISIBLE_TEXT.contains("OpenMapTiles"))
    }

    @Test
    fun editablePointsAndVehicleRemainAboveRouteLines() {
        val order = OverlayLayerOrder.orderedLayerIds

        assertEquals(order.distinct(), order)
        assertTrue(order.indexOf(OverlayLayerOrder.PLAN_PREVIEW) > order.indexOf(OverlayLayerOrder.ACTIVE_ROUTE))
        assertTrue(order.indexOf(OverlayLayerOrder.START) > order.indexOf(OverlayLayerOrder.PLAN_PREVIEW))
        assertEquals(OverlayLayerOrder.VEHICLE, order.last())
    }
}
