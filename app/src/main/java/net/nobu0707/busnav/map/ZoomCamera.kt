package net.nobu0707.busnav.map

import org.maplibre.android.camera.CameraPosition

internal fun zoomOnlyCamera(camera: CameraPosition, direction: Int, minimum: Double, maximum: Double): CameraPosition =
    CameraPosition.Builder(camera)
        .zoom(MapControlsPolicy.steppedZoom(camera.zoom, direction, minimum, maximum)).build()
