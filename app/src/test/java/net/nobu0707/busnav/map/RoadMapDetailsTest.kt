package net.nobu0707.busnav.map

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.map.RoadMapDetails.FacilityType.*
import org.junit.Assert.*
import org.junit.Test

class RoadMapDetailsTest {
    @Test fun trafficAndRoutePointsRemainAboveAllRouteLines() {
        val order = net.nobu0707.busnav.map.basemap.OverlayLayerOrder
        order.routeLineIds.forEach { line ->
            assertTrue(order.orderedLayerIds.indexOf(line) < order.orderedLayerIds.indexOf(order.TRAFFIC_MARKER))
        }
        assertTrue(order.orderedLayerIds.indexOf(order.TRAFFIC_MARKER) < order.orderedLayerIds.indexOf(order.ACTIVE_START))
        assertEquals(order.VEHICLE, order.orderedLayerIds.last())
    }
    @Test fun facilityUsesExplicitDesignationsAndSafeFallbacks() {
        fun facility(name: String) = RoadMapDetails.facility(mapOf("highway" to "motorway_junction", "name" to name), true)
        assertEquals(JUNCTION, facility("三宅坂JCT"))
        assertEquals(JUNCTION, facility("有明ジャンクション"))
        assertEquals(ENTRANCE, facility("汐留入口"))
        assertEquals(EXIT, facility("芝公園出口"))
        assertEquals(INTERCHANGE, facility("霞が関"))
        assertEquals(INTERCHANGE, facility("霞が関出入口"))
        assertEquals(UNKNOWN, RoadMapDetails.facility(mapOf("name" to "三宅坂JCT"), true))
        val toll = mapOf("barrier" to "toll_booth")
        assertEquals(TOLL_GATE, RoadMapDetails.facility(toll, true))
        assertEquals(UNKNOWN, RoadMapDetails.facility(toll, false))
        assertEquals(MAINLINE_TOLL_GATE, RoadMapDetails.facility(toll + ("name" to "本線料金所"), true))
    }
    @Test fun intersectionsNeedTheirOwnNamesAndRankDoesNotInventNames() {
        assertTrue(RoadMapDetails.namedIntersection(mapOf("highway" to "traffic_signals", "name" to "青山一丁目")))
        assertFalse(RoadMapDetails.namedIntersection(mapOf("highway" to "traffic_signals")))
        assertFalse(RoadMapDetails.namedIntersection(mapOf("name" to "店舗")))
        assertFalse(RoadMapDetails.namedIntersection(mapOf("highway" to "crossing", "name" to "横断歩道")))
        assertEquals(0, RoadMapDetails.intersectionRank(2, true))
        assertEquals(1, RoadMapDetails.intersectionRank(1, true))
    }
    @Test fun spanHysteresisAndInvalidProjection() {
        val policy = ShieldSpanPolicy()
        assertFalse(policy.update(3000.0))
        assertFalse(policy.update(2400.0))
        assertTrue(policy.update(2000.0))
        assertTrue(policy.update(2400.0))
        assertFalse(policy.update(3000.0))
        assertTrue(policy.update(1500.0))
        assertFalse(policy.update(Double.NaN))
    }
    @Test fun projectionUsesVisibleRectangleInBothOrientations() {
        val rect = VisibleMapSpanCalculator.Rect(0f, 40f, 400f, 640f)
        val samples = mutableListOf<VisibleMapSpanCalculator.ScreenPoint>()
        val distance = VisibleMapSpanCalculator.measure(rect) {
            samples += it
            GeoPoint(35.0 + it.y / 10000, 139.0)
        }
        assertEquals(listOf(VisibleMapSpanCalculator.ScreenPoint(200f,40f), VisibleMapSpanCalculator.ScreenPoint(200f,640f)), samples)
        assertEquals(6671.7, distance, 2.0)
        assertTrue(VisibleMapSpanCalculator.measure(rect.copy(right = 1200f)) { GeoPoint(35.0 + it.y / 10000, 139.0) } > 6600)
        assertTrue(VisibleMapSpanCalculator.measure(rect.copy(bottom = 0f)) { GeoPoint(0.0,0.0) }.isNaN())
    }
}
