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

/** Overlay measurements are local to the full-size MapView, in physical pixels. */
fun navigationMapFrame(
    location: LocationState,
    camera: NavigationCameraState,
    currentBearing: Double,
    mapHeightPx: Int,
    bottomOcclusionPx: Int,
    density: Float = 1f,
    vehicleOuterRadiusDp: Float = 25.9f,
    topOverlayBottomPx: Int = 0,
): NavigationMapFrame {
    val bearing = camera.targetBearing(currentBearing)
    val viewport = VisibleMapViewport(1, mapHeightPx,
        MapViewportInsets(bottom = bottomOcclusionPx))
    val occlusion = mapHeightPx.coerceAtLeast(1) - viewport.rect.bottom.toInt()
    val visibleHeight = viewport.rect.height.toDouble()
    val lowerAnchor = camera.active && camera.following && camera.orientation == NavigationMapOrientation.HEADING_UP
    // Outer marker stroke radius + 8 dp clearance. Safety wins for tiny viewports.
    val margin = maxOf(vehicleOuterRadiusDp + 8f, 24f) * density
    val desiredCenterY = (viewport.rect.bottom - margin).coerceAtLeast(0f).toDouble()
    val minimumAllowedY = topOverlayBottomPx + margin
    // If overlays leave insufficient room, bottom safety takes priority. There is
    // no collision-free point in that case; never substitute the viewport center.
    val centerY = if (minimumAllowedY <= desiredCenterY) desiredCenterY.coerceAtLeast(minimumAllowedY.toDouble())
        else desiredCenterY
    val offset = 2 * centerY - visibleHeight
    // Native padding is non-negative. Negative offsets use additional bottom
    // padding instead of forcing a centered fallback in a short viewport.
    val topPadding = if (lowerAnchor) offset.coerceAtLeast(0.0) else 0.0
    val bottomPadding = if (lowerAnchor) occlusion - offset.coerceAtMost(0.0) else 0.0
    return NavigationMapFrame(location, location.point, bearing,
        camera.vehicleScreenRotation(bearing, location.normalizedBearingDegrees?.toDouble()),
        location.elapsedRealtimeMillis, topPadding, bottomPadding)
}

fun shouldApplyMapFrame(lastElapsed: Long?, incomingElapsed: Long?): Boolean =
    lastElapsed == null || incomingElapsed == null || incomingElapsed >= lastElapsed
