@file:Suppress("LogNotTimber")

package net.nobu0707.busnav.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import androidx.core.graphics.createBitmap
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
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
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlan
import net.nobu0707.busnav.map.basemap.BasemapConfig
import net.nobu0707.busnav.map.basemap.BasemapController
import net.nobu0707.busnav.map.basemap.BasemapState
import net.nobu0707.busnav.map.basemap.MapDiagnostics
import net.nobu0707.busnav.map.basemap.OverlayLayerOrder

class MapController(
    private val onReady: () -> Unit,
    private val onGesture: () -> Unit,
    private val onError: (String) -> Unit,
    private val onLongPress: ((GeoPoint) -> Unit)?,
    private val routePaddingPx: Int,
    basemapConfig: BasemapConfig,
    mapDiagnostics: MapDiagnostics,
    onBasemapStateChanged: (BasemapState) -> Unit,
) {
    private var map: MapLibreMap? = null
    private var mapView: MapView? = null
    private var style: Style? = null
    private var latestLocation: LocationState? = null
    private var hasCenteredOnFirstLocation = false
    private var lastRecenterRequestId = 0
    private var lastFollowedTimestampMillis: Long? = null
    private var latestRoute: ScheduledRoute? = null
    private var latestRouteOverviewRequestId = 0
    private var lastRouteOverviewRequestId = 0
    private var latestRoutePlan: RoutePlan? = null
    private var latestPlanOverviewRequestId = 0
    private var lastPlanOverviewRequestId = 0
    private val routeOverlay = RouteOverlayController()
    private val routePlanOverlay = RoutePlanOverlayController()
    private val basemapController = BasemapController(
        config = basemapConfig,
        diagnostics = mapDiagnostics,
        onStateChanged = onBasemapStateChanged,
    )
    private var readyDelivered = false
    private val styleLoadedListener = MapView.OnDidFinishLoadingStyleListener {
        map?.style?.let { loadedStyle ->
            style = loadedStyle
            basemapController.onStyleLoaded()
            installOverlays(loadedStyle)
            if (!readyDelivered) {
                readyDelivered = true
                onReady()
            }
        }
    }
    private val mapLoadFailedListener = MapView.OnDidFailLoadingMapListener { error ->
        val fallbackStyle = basemapController.onMapLoadFailed(error)
        if (fallbackStyle != null) {
            map?.setStyle(fallbackStyle)
        } else if (basemapController.isFallbackActive) {
            onError("Embedded fallback map style failed to load")
        }
    }

    private val moveListener = object : MapLibreMap.OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) = onGesture()
        override fun onMove(detector: MoveGestureDetector) = Unit
        override fun onMoveEnd(detector: MoveGestureDetector) = Unit
    }
    private val longClickListener = MapLibreMap.OnMapLongClickListener { latLng ->
        val callback = onLongPress ?: return@OnMapLongClickListener false
        runCatching { GeoPoint(latLng.latitude, latLng.longitude) }
            .onSuccess(callback)
            .onFailure { error -> Log.w(TAG, "Ignoring invalid long-press coordinate", error) }
        true
    }

    fun attach(mapView: MapView) {
        this.mapView = mapView
        mapView.addOnDidFinishLoadingStyleListener(styleLoadedListener)
        mapView.addOnDidFailLoadingMapListener(mapLoadFailedListener)
        mapView.getMapAsync { mapLibreMap ->
            map = mapLibreMap
            mapLibreMap.addOnMoveListener(moveListener)
            if (onLongPress != null) mapLibreMap.addOnMapLongClickListener(longClickListener)
            mapLibreMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM),
            )
            runCatching {
                mapLibreMap.setStyle(basemapController.initialStyle())
            }.onFailure { error ->
                val fallbackStyle = basemapController.onMapLoadFailed(error.message.orEmpty())
                if (fallbackStyle != null) {
                    mapLibreMap.setStyle(fallbackStyle)
                } else {
                    onError(error.message ?: "Map style could not be loaded")
                }
            }
        }
    }

    private fun installOverlays(loadedStyle: Style) {
        runCatching {
            routeOverlay.setRoute(latestRoute)
            routeOverlay.install(loadedStyle)
            routePlanOverlay.setRoutePlan(latestRoutePlan)
            routePlanOverlay.install(loadedStyle)
            installVehicleLayer(loadedStyle)
            latestLocation?.let(::renderLocation)
            fitRouteIfRequested()
            fitRoutePlanIfRequested()
        }.onFailure { error ->
            Log.e(TAG, "Unable to install map overlays", error)
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

    fun updateRoute(route: ScheduledRoute?, routeOverviewRequestId: Int) {
        val routeChanged = route != latestRoute
        latestRoute = route
        latestRouteOverviewRequestId = routeOverviewRequestId
        routeOverlay.setRoute(route)
        if (routeChanged) {
            style?.let { loadedStyle ->
                runCatching { routeOverlay.render(loadedStyle) }
                    .onFailure { error -> Log.e(TAG, "Unable to render scheduled route", error) }
            }
        }
        fitRouteIfRequested()
    }

    fun updateRoutePlan(routePlan: RoutePlan?, planOverviewRequestId: Int) {
        val planChanged = routePlan != latestRoutePlan
        latestRoutePlan = routePlan
        latestPlanOverviewRequestId = planOverviewRequestId
        routePlanOverlay.setRoutePlan(routePlan)
        if (planChanged) {
            style?.let { loadedStyle ->
                runCatching { routePlanOverlay.render(loadedStyle) }
                    .onFailure { error -> Log.e(TAG, "Unable to render route plan preview", error) }
            }
        }
        fitRoutePlanIfRequested()
    }

    fun updateBasemap(config: BasemapConfig) {
        val nextStyle = basemapController.updateConfig(config) ?: return
        style = null
        map?.setStyle(nextStyle)
    }

    fun detach() {
        mapView?.removeOnDidFinishLoadingStyleListener(styleLoadedListener)
        mapView?.removeOnDidFailLoadingMapListener(mapLoadFailedListener)
        map?.removeOnMoveListener(moveListener)
        if (onLongPress != null) map?.removeOnMapLongClickListener(longClickListener)
        mapView = null
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

    private fun fitRoute(route: ScheduledRoute) {
        val bounds = LatLngBounds.Builder().apply {
            route.geometry.points.forEach { include(it.toLatLng()) }
        }.build()
        map?.easeCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, routePaddingPx),
            RECENTER_ANIMATION_MILLIS,
        )
    }

    private fun fitRouteIfRequested() {
        val route = latestRoute ?: return
        if (
            map == null || style == null ||
            latestRouteOverviewRequestId == 0 ||
            latestRouteOverviewRequestId == lastRouteOverviewRequestId
        ) {
            return
        }
        fitRoute(route)
        lastRouteOverviewRequestId = latestRouteOverviewRequestId
    }

    private fun fitRoutePlanIfRequested() {
        val points = latestRoutePlan?.points.orEmpty()
        if (
            map == null || style == null || points.isEmpty() ||
            latestPlanOverviewRequestId == 0 ||
            latestPlanOverviewRequestId == lastPlanOverviewRequestId
        ) {
            return
        }
        if (points.size == 1) {
            map?.easeCamera(
                CameraUpdateFactory.newLatLngZoom(points.single().position.toLatLng(), PLAN_POINT_ZOOM),
                RECENTER_ANIMATION_MILLIS,
            )
        } else {
            runCatching {
                val bounds = LatLngBounds.Builder().apply {
                    points.forEach { include(it.position.toLatLng()) }
                }.build()
                map?.easeCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, routePaddingPx),
                    RECENTER_ANIMATION_MILLIS,
                )
            }.onFailure { error -> Log.w(TAG, "Unable to fit route plan bounds", error) }
        }
        lastPlanOverviewRequestId = latestPlanOverviewRequestId
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
        const val VEHICLE_SOURCE_ID = "busnav-vehicle-source"
        const val VEHICLE_LAYER_ID = OverlayLayerOrder.VEHICLE
        const val VEHICLE_ICON_ID = "busnav-vehicle-icon"
        const val DEFAULT_ZOOM = 4.5
        const val FOLLOW_ZOOM = 16.5
        const val PLAN_POINT_ZOOM = 15.0
        const val FOLLOW_ANIMATION_MILLIS = 450
        const val RECENTER_ANIMATION_MILLIS = 750
        const val TAG = "BusNavMapController"
        val DEFAULT_LOCATION = LatLng(36.2048, 138.2529)
    }
}
