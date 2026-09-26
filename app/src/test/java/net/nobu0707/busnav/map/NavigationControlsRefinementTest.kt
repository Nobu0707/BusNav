package net.nobu0707.busnav.map

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.location.LocationState
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng

class NavigationControlsRefinementTest {
    @Test fun zoomChangesOnlyZoomAndClampsRepeatedTaps() {
        val initial = CameraPosition.Builder().target(LatLng(35.68, 139.76)).zoom(15.0)
            .bearing(90.0).tilt(25.0).padding(0.0, 300.0, 0.0, 100.0).build()
        for (direction in listOf(1, -1)) {
            val stepped = zoomOnlyCamera(initial, direction, 2.0, 20.0)
            assertEquals(15.0 + direction, stepped.zoom, 0.0)
            var camera = initial
            repeat(100) { camera = zoomOnlyCamera(camera, direction, 2.0, 20.0) }
            assertEquals(if (direction > 0) 20.0 else 2.0, camera.zoom, 0.0)
            assertEquals(initial.target, camera.target)
            assertEquals(initial.bearing, camera.bearing, 0.0)
            assertEquals(initial.tilt, camera.tilt, 0.0)
            assertArrayEquals(initial.padding, camera.padding, 0.0)
        }
    }

    @Test fun rulerNeverExceedsMaximumIncludingHysteresisAndFallback() {
        val nice = setOf(10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000)
        assertTrue(MapControlsPolicy.RULER_CARD_WIDTH_DP <= 72f)
        assertEquals(12f, MapControlsPolicy.RULER_CARD_WIDTH_DP - MapControlsPolicy.RULER_MAX_BAR_DP, 0f)
        var previous: ScaleRulerReading? = null
        for (mpp in listOf(0.01, 0.2, 0.35, 0.5, 0.9, 1.0, 1.8, 2.0, 4.0, 10.0, 40.0, 200.0, 1000.0)) {
            val reading = ScaleRulerPolicy.choose(mpp, 28f, 56f, previous)
            reading?.let {
                assertTrue(it.widthPx <= 56f)
                assertTrue(it.distanceMeters in nice)
                assertEquals(it.distanceMeters.toDouble(), it.widthPx * mpp, 0.001)
            }
            previous = reading
        }
        assertEquals("10 km", ScaleRulerPolicy.choose(200.0, 28f, 56f)!!.label)
        assertNull(ScaleRulerPolicy.choose(0.01, 28f, 56f))
        val old = ScaleRulerReading(100, 55f)
        assertTrue(ScaleRulerPolicy.choose(1.7, 28f, 56f, old)!!.widthPx <= 56f)
    }

    @Test fun anchorUsesDensityAndOuterStrokeClearanceAcrossViewports() {
        val location = LocationState(GeoPoint(35.68, 139.76), 5f, 90f, 8f, 1000, 1000)
        val camera = NavigationCameraState(true, true, NavigationMapOrientation.HEADING_UP, 90.0)
        for (density in listOf(1f, 2f, 3f)) {
            for (height in listOf(800, 400)) {
                val frame = navigationMapFrame(location, camera, 90.0, (height * density).toInt(), (128 * density).toInt(), density)
                val center = (frame.topPaddingPx + height * density - frame.bottomPaddingPx) / 2
                val visible = (height - 128) * density
                assertEquals(33.9 * density, visible - center, 0.001)
                assertTrue(center / visible > 0.85)
                assertSame(location, frame.location)
                assertEquals(location.point, frame.cameraTarget)
            }
        }
        val tiny = navigationMapFrame(location, camera, 90.0, 50, 40)
        assertTrue(tiny.topPaddingPx >= 0.0)
    }
    @Test fun shortLandscapeDoesNotFallBackToVisibleCenter() {
        val fix = LocationState(GeoPoint(35.68, 139.76), 5f, 90f, 8f, 1, 1)
        val camera = NavigationCameraState(true, true, NavigationMapOrientation.HEADING_UP, 90.0)
        // 62px usable: old max(desired, height/2) forced the point to 31px.
        val frame = navigationMapFrame(fix, camera, 90.0, 190, 128)
        val y = (frame.topPaddingPx + 190 - frame.bottomPaddingPx) / 2
        assertEquals(62.0 - 33.9, y, 0.001)
        assertTrue(y < 31.0)
        assertTrue(frame.topPaddingPx >= 0 && frame.bottomPaddingPx >= 0)
    }

    @Test fun northUpAndNonFollowingKeepPhysicalCenter() {
        val fix = LocationState(GeoPoint(35.68, 139.76), 5f, 90f, 8f, 1, 1)
        val heading = NavigationCameraState(true, true, NavigationMapOrientation.HEADING_UP, 90.0)
        for (camera in listOf(heading.copy(orientation = NavigationMapOrientation.NORTH_UP),
            heading.copy(following = false), heading.copy(active = false))) {
            val frame = navigationMapFrame(fix, camera, 90.0, 360, 80, topOverlayBottomPx = 90)
            assertEquals(0.0, frame.topPaddingPx, 0.0)
            assertEquals(0.0, frame.bottomPaddingPx, 0.0)
        }
    }

    @Test fun bottomAnchorClearsTopOverlayWhenTheMarkerFits() {
        val fix = LocationState(GeoPoint(35.68, 139.76), 5f, 90f, 8f, 1, 1)
        val camera = NavigationCameraState(true, true, NavigationMapOrientation.HEADING_UP, 90.0)
        for (height in listOf(360, 800)) {
            val frame = navigationMapFrame(fix, camera, 90.0, height, 80, topOverlayBottomPx = 90)
            val y = (frame.topPaddingPx + height - frame.bottomPaddingPx) / 2
            assertEquals(height - 80 - 33.9, y, 0.001)
            assertTrue(y - 25.9 >= 90 + 8)
        }
    }

}
