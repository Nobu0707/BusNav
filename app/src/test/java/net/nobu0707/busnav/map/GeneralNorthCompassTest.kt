package net.nobu0707.busnav.map

import net.nobu0707.busnav.domain.navigation.northScreenRotation
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng

class GeneralNorthCompassTest {
    @Test fun cardinalRotationsAndVisibilityUseNavigationConvention() {
        for ((bearing, rotation) in listOf(0.0 to 0.0, 90.0 to 270.0, 180.0 to 180.0, 270.0 to 90.0)) {
            assertEquals(rotation, northScreenRotation(bearing), 0.0)
            assertEquals(bearing != 0.0, MapControlsPolicy.generalCompassVisible(false, bearing))
            assertFalse(MapControlsPolicy.generalCompassVisible(true, bearing))
        }
    }

    @Test fun wrapBoundaryStaysNeutralAndRotatesContinuously() {
        assertEquals(1.0, northScreenRotation(359.0), 0.0)
        assertEquals(359.0, northScreenRotation(1.0), 0.0)
        for (bearing in listOf(359.0, 1.0, 360.0, -1.0)) {
            assertFalse(MapControlsPolicy.generalCompassVisible(false, bearing))
        }
        for (bearing in listOf(4.0, 356.0)) {
            assertTrue(MapControlsPolicy.generalCompassVisible(false, bearing))
        }
    }

    @Test fun northResetPreservesTargetZoomTiltAndPadding() {
        val before = CameraPosition.Builder().target(LatLng(35.68, 139.76)).zoom(15.5)
            .bearing(270.0).tilt(30.0).padding(1.0, 2.0, 3.0, 4.0).build()
        val after = northUpCamera(before)
        assertEquals(0.0, after.bearing, 0.0)
        assertEquals(before.target, after.target)
        assertEquals(before.zoom, after.zoom, 0.0)
        assertEquals(before.tilt, after.tilt, 0.0)
        assertArrayEquals(before.padding, after.padding, 0.0)
        assertEquals(270.0, before.bearing, 0.0)
    }
}
