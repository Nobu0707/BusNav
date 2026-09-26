package net.nobu0707.busnav.domain.navigation

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.model.MapViewportInsets
import net.nobu0707.busnav.domain.model.VisibleMapViewport
import net.nobu0707.busnav.location.LocationState

data class NavigationMapFrame(
    val location: LocationState,
    val cameraTarget: GeoPoint,
    val cameraBearing: Double,
    val markerScreenRotation: Double,
    val elapsedTimestamp: Long?,
    val topPaddingPx: Double,
    val bottomPaddingPx: Double,
)

/** The map view already excludes the guidance and operations panels. Only controls
 * drawn over that view are subtracted from its visible rectangle.
 */
fun navigationMapFrame(
    location: LocationState,
    camera: NavigationCameraState,
    currentBearing: Double,
    mapHeightPx: Int,
    bottomOcclusionPx: Int,
    density: Float = 1f,
    vehicleOuterRadiusDp: Float = 25.9f,
): NavigationMapFrame {
    val bearing = camera.targetBearing(currentBearing)
    val viewport = VisibleMapViewport(1, mapHeightPx,
        MapViewportInsets(bottom = bottomOcclusionPx))
    val occlusion = mapHeightPx.coerceAtLeast(1) - viewport.rect.bottom.toInt()
    val visibleHeight = viewport.rect.height.toDouble()
    val lowerAnchor = camera.active && camera.following && camera.orientation == NavigationMapOrientation.HEADING_UP
    // Outer marker stroke radius + 8 dp clearance. Safety wins for tiny viewports.
    val margin = maxOf(vehicleOuterRadiusDp + 8f, 24f) * density
    val centerY = (visibleHeight - margin).coerceAtLeast(visibleHeight / 2)
    val topPadding = if (lowerAnchor) 2 * centerY - visibleHeight else 0.0
    val bottomPadding = if (lowerAnchor) occlusion.toDouble() else 0.0
    return NavigationMapFrame(location, location.point, bearing,
        camera.vehicleScreenRotation(bearing, location.normalizedBearingDegrees?.toDouble()),
        location.elapsedRealtimeMillis, topPadding, bottomPadding)
}

fun shouldApplyMapFrame(lastElapsed: Long?, incomingElapsed: Long?): Boolean =
    lastElapsed == null || incomingElapsed == null || incomingElapsed >= lastElapsed
