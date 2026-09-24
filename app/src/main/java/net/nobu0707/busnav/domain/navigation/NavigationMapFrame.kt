package net.nobu0707.busnav.domain.navigation

import net.nobu0707.busnav.domain.model.GeoPoint
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
): NavigationMapFrame {
    val bearing = camera.targetBearing(currentBearing)
    val occlusion = bottomOcclusionPx.coerceIn(0, (mapHeightPx - 1).coerceAtLeast(0))
    val visibleHeight = (mapHeightPx - occlusion).coerceAtLeast(1)
    val lowerAnchor = camera.active && camera.following && camera.orientation == NavigationMapOrientation.HEADING_UP
    val topPadding = if (lowerAnchor) visibleHeight * 0.44 else 0.0
    val bottomPadding = if (lowerAnchor) occlusion.toDouble() else 0.0
    return NavigationMapFrame(location, location.point, bearing,
        camera.vehicleScreenRotation(bearing, location.normalizedBearingDegrees?.toDouble()),
        location.elapsedRealtimeMillis, topPadding, bottomPadding)
}

fun shouldApplyMapFrame(lastElapsed: Long?, incomingElapsed: Long?): Boolean =
    lastElapsed == null || incomingElapsed == null || incomingElapsed >= lastElapsed
