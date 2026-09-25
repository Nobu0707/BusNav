package net.nobu0707.busnav.map

import net.nobu0707.busnav.domain.model.MapViewportInsets
import net.nobu0707.busnav.domain.model.VisibleMapViewport
import org.junit.Assert.*
import org.junit.Test

class MapControlsViewportTest {
    @Test fun viewportClampsInsetsAndCentersOnUncoveredMap() {
        val full = VisibleMapViewport(400, 800).rect
        assertEquals(400f, full.centerY, 0f)
        for (bottom in listOf(104, 320, 672)) {
            val rect = VisibleMapViewport(400, 800, MapViewportInsets(bottom = bottom)).rect
            assertEquals((800 - bottom) / 2f, rect.centerY, 0f)
            assertEquals(rect.centerY, rect.yAt(0.5f), 0f)
            assertEquals(rect.centerY, rect.centerY, 0f) // cursor pixel sent to projection
        }
        val landscape = VisibleMapViewport(900, 360,
            MapViewportInsets(left = 300, bottom = 80)).rect
        assertEquals(600f, landscape.centerX, 0f)
        assertEquals(238f, landscape.yAt(0.85f), 0.01f)
        val invalid = VisibleMapViewport(100, 50, MapViewportInsets(-5, 100, 1000, 1000)).rect
        assertTrue(invalid.width > 0f && invalid.height > 0f)
    }

    @Test fun compassAndPresetsKeepNavigationPolicySeparate() {
        assertFalse(MapControlsPolicy.generalCompassVisible(false, 0.0))
        assertFalse(MapControlsPolicy.generalCompassVisible(false, 359.0))
        assertTrue(MapControlsPolicy.generalCompassVisible(false, 30.0))
        assertFalse(MapControlsPolicy.generalCompassVisible(true, 30.0))
        assertEquals(ScalePreset.NORMAL, MapControlsPolicy.nextPreset(ScaleMode.CUSTOM))
        assertEquals(ScalePreset.WIDE, MapControlsPolicy.nextPreset(ScaleMode.NORMAL))
        assertEquals(ScalePreset.NEAR, MapControlsPolicy.nextPreset(ScaleMode.WIDE))
        assertTrue(ScalePreset.NEAR.spanMeters < ScalePreset.NORMAL.spanMeters)
        assertTrue(ScalePreset.NORMAL.spanMeters < ScalePreset.WIDE.spanMeters)
        assertEquals(16.0, MapControlsPolicy.zoomForSpan(15.0, 2000.0, 1000.0), 0.001)
    }

    @Test fun rulerUsesNiceProjectedDistanceAndHysteresis() {
        val first = ScaleRulerPolicy.choose(1.0, 80f, 140f)!!
        assertEquals(100, first.distanceMeters)
        assertEquals("100 m", first.label)
        assertEquals(100f, first.widthPx, 0f)
        val held = ScaleRulerPolicy.choose(1.05, 80f, 140f, first)!!
        assertEquals(100, held.distanceMeters)
        assertEquals("1 km", ScaleRulerReading(1000, 100f).label)
        assertNull(ScaleRulerPolicy.choose(Double.NaN, 80f, 140f))
    }
}
