package net.nobu0707.busnav.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import net.nobu0707.busnav.location.LocationState

class MapController(
    private val onReady: () -> Unit,
    private val onGesture: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var latestLocation: LocationState? = null
    private var hasCenteredOnFirstLocation = false
    private var lastRecenterRequestId = 0
    private var lastFollowedTimestampMillis: Long? = null

    private val moveListener = object : MapLibreMap.OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) = onGesture()
        override fun onMove(detector: MoveGestureDetector) = Unit
        override fun onMoveEnd(detector: MoveGestureDetector) = Unit
    }

    fun attach(mapView: MapView) {
        mapView.addOnDidFailLoadingMapListener { error ->
            onError("地図を読み込めませんでした: $error")
        }
        mapView.getMapAsync { mapLibreMap ->
            map = mapLibreMap
            mapLibreMap.addOnMoveListener(moveListener)
            mapLibreMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM),
            )
            runCatching {
                mapLibreMap.setStyle(STYLE_URL) { loadedStyle ->
                    style = loadedStyle
                    installVehicleLayer(loadedStyle)
                    latestLocation?.let(::renderLocation)
                    onReady()
                }
            }.onFailure { error ->
                onError(error.message ?: "地図スタイルを読み込めませんでした")
            }
        }
    }

    fun update(location: LocationState?, isFollowing: Boolean, recenterRequestId: Int) {
        latestLocation = location
        location?.let(::renderLocation)

        if (location != null && isFollowing && (!hasCenteredOnFirstLocation || recenterRequestId != lastRecenterRequestId)) {
            centerOn(location)
            hasCenteredOnFirstLocation = true
            lastRecenterRequestId = recenterRequestId
            lastFollowedTimestampMillis = location.timestampMillis
        } else if (
            location != null &&
            isFollowing &&
            hasCenteredOnFirstLocation &&
            location.timestampMillis != lastFollowedTimestampMillis
        ) {
            map?.easeCamera(CameraUpdateFactory.newLatLng(location.point.toLatLng()), FOLLOW_ANIMATION_MILLIS)
            lastFollowedTimestampMillis = location.timestampMillis
        }
    }

    fun detach() {
        map?.removeOnMoveListener(moveListener)
        map = null
        style = null
    }

    private fun installVehicleLayer(loadedStyle: Style) {
        if (loadedStyle.getSource(VEHICLE_SOURCE_ID) == null) {
            loadedStyle.addSource(
                GeoJsonSource(
                    VEHICLE_SOURCE_ID,
                    Feature.fromGeometry(Point.fromLngLat(DEFAULT_LOCATION.longitude, DEFAULT_LOCATION.latitude)),
                ),
            )
        }
        loadedStyle.addImage(VEHICLE_ICON_ID, createVehicleIcon())
        if (loadedStyle.getLayer(VEHICLE_LAYER_ID) == null) {
            loadedStyle.addLayer(
                SymbolLayer(VEHICLE_LAYER_ID, VEHICLE_SOURCE_ID).withProperties(
                    iconImage(VEHICLE_ICON_ID),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                    iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                ),
            )
        }
    }

    private fun renderLocation(location: LocationState) {
        val loadedStyle = style ?: return
        (loadedStyle.getSource(VEHICLE_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
            Feature.fromGeometry(Point.fromLngLat(location.point.longitude, location.point.latitude)),
        )
        loadedStyle.getLayer(VEHICLE_LAYER_ID)?.setProperties(
            iconRotate(location.normalizedBearingDegrees ?: 0f),
        )
    }

    private fun centerOn(location: LocationState) {
        map?.easeCamera(
            CameraUpdateFactory.newLatLngZoom(location.point.toLatLng(), FOLLOW_ZOOM),
            RECENTER_ANIMATION_MILLIS,
        )
    }

    private fun net.nobu0707.busnav.domain.model.GeoPoint.toLatLng() = LatLng(latitude, longitude)

    private fun createVehicleIcon(): Bitmap {
        val size = 72
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 11, 25, 35)
            style = Paint.Style.FILL
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(121, 184, 209)
            style = Paint.Style.FILL
        }
        val path = Path().apply {
            moveTo(size / 2f, 5f)
            lineTo(size - 9f, size - 8f)
            lineTo(size / 2f, size - 22f)
            lineTo(9f, size - 8f)
            close()
        }
        canvas.drawPath(path, halo)
        val inner = Path().apply {
            moveTo(size / 2f, 15f)
            lineTo(size - 20f, size - 20f)
            lineTo(size / 2f, size - 31f)
            lineTo(20f, size - 20f)
            close()
        }
        canvas.drawPath(inner, fill)
        return bitmap
    }

    private companion object {
        const val STYLE_URL = "https://demotiles.maplibre.org/style.json"
        const val VEHICLE_SOURCE_ID = "busnav-vehicle-source"
        const val VEHICLE_LAYER_ID = "busnav-vehicle-layer"
        const val VEHICLE_ICON_ID = "busnav-vehicle-icon"
        const val DEFAULT_ZOOM = 4.5
        const val FOLLOW_ZOOM = 16.5
        const val FOLLOW_ANIMATION_MILLIS = 450
        const val RECENTER_ANIMATION_MILLIS = 750
        val DEFAULT_LOCATION = LatLng(36.2048, 138.2529)
    }
}
