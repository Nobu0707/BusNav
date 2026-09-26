@file:Suppress("LogNotTimber")

package net.nobu0707.busnav.map

import net.nobu0707.busnav.domain.navigation.*
import android.graphics.PointF
import net.nobu0707.busnav.ui.routeplan.EditorCamera
import net.nobu0707.busnav.ui.routeplan.EditorCameraRequest
import org.maplibre.android.camera.CameraPosition
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
import net.nobu0707.busnav.domain.model.MapViewportInsets
import net.nobu0707.busnav.domain.model.VisibleMapViewport
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
    private val initialCamera: EditorCamera? = null,
    private val initialNavigationCamera: Boolean = false,
    private val onNavigationCameraInitialized: () -> Unit = {},
    private val onCameraChanged: (EditorCamera) -> Unit = {},
    private val onScaleGesture: () -> Unit = {},
    private val onRulerChanged: (ScaleRulerReading?) -> Unit = {},
) {
    private var map: MapLibreMap? = null
    private var mapView: MapView? = null
    private var style: Style? = null
    private var latestLocation: LocationState? = null
    private var deviceHeadingDegrees: Double? = null
    private var navigationCamera = NavigationCameraState()
    private var navigationInitialized = initialNavigationCamera
    private val headingConfig = NavigationHeadingConfig()
    private val headingFreshness = NavigationHeadingResolver(headingConfig)
    private var gestureSuspended = false
    private var lastFollowedElapsedMillis: Long? = null
    private var gestureZoomAtStart: Double? = null
    private var lastRuler: ScaleRulerReading? = null
    private var navigationBottomOcclusionPx = 0
    private val cameraStartedListener = MapLibreMap.OnCameraMoveStartedListener { reason ->
        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
            gestureSuspended = true
            gestureZoomAtStart = map?.cameraPosition?.zoom
            onGesture()
        }
    }
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
    private val detourOverlay = DetourOverlayController()
    private val trafficOverlay = TrafficOverlayController()
    private val basemapController = BasemapController(
        config = basemapConfig,
        diagnostics = mapDiagnostics,
        onStateChanged = onBasemapStateChanged,
    )
    private val tunnelProvider: TunnelStateProvider = TransportationTunnelProvider { style }
    private val tunnelHysteresis = TunnelHysteresis()
    fun resetTunnel() = tunnelHysteresis.reset()
    fun sampleTunnel(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        val location = latestLocation
        val age = location?.elapsedRealtimeMillis?.let { now - it }
        val observation = if (location != null && age != null && age in 0..10_000 &&
            location.accuracyMeters?.let { it.isFinite() && it in 0f..30f } == true) {
            tunnelProvider.observe(location.point)
        } else TunnelObservation.UNKNOWN
        return tunnelHysteresis.update(observation, now)
    }

    private var editorRequest: EditorCameraRequest? = null
    private var editorBottomPadding: Int? = null
    private var onEditorCameraApplied: (Long) -> Unit = {}
    private var lastEditorRequestId: Long? = null
    private val cameraListener = MapLibreMap.OnCameraMoveListener { saveCamera(); scheduleMapDetails() }
    private val layoutListener = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitEditorIfRequested(); scheduleMapDetails() }

    private fun saveCamera() {
        val camera = map?.cameraPosition ?: return
        val target = camera.target ?: return
        if (gestureZoomAtStart?.let { kotlin.math.abs(it - camera.zoom) > 0.01 } == true) {
            gestureZoomAtStart = null
            onScaleGesture()
        }
        latestLocation?.let(::renderVehicleRotation)
        onCameraChanged(EditorCamera(GeoPoint(target.latitude, target.longitude), camera.zoom, camera.bearing, camera.tilt))
    }

    fun cursorPosition(): GeoPoint? {
        val view = mapView ?: return null
        if (view.width == 0 || view.height == 0) return null
        val rect = visibleViewport().rect
        val point = map?.projection?.fromScreenLocation(PointF(rect.centerX, rect.centerY)) ?: return null
        return GeoPoint(point.latitude, point.longitude)
    }

    private fun visibleViewport(): VisibleMapViewport {
        val view = mapView
        val bottom = if (navigationCamera.active) navigationBottomOcclusionPx else editorBottomPadding ?: 0
        return VisibleMapViewport(view?.width ?: 0, view?.height ?: 0,
            MapViewportInsets(bottom = bottom))
    }

    private fun visibleSpan(): Double {
        val native = map ?: return Double.NaN
        val rect = visibleViewport().rect
        return VisibleMapSpanCalculator.measure(
            VisibleMapSpanCalculator.Rect(rect.left, rect.top, rect.right, rect.bottom)) {
            val point = native.projection.fromScreenLocation(PointF(it.x, it.y))
            GeoPoint(point.latitude, point.longitude)
        }
    }

    fun resetNorth() {
        val native = map ?: return
        val current = native.cameraPosition
        native.easeCamera(CameraUpdateFactory.newCameraPosition(
            northUpCamera(current)), RECENTER_ANIMATION_MILLIS)
    }

    fun applyScalePreset(preset: ScalePreset) {
        val native = map ?: return
        val camera = native.cameraPosition
        val zoom = MapControlsPolicy.zoomForSpan(camera.zoom, visibleSpan(), preset.spanMeters)
        native.easeCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder(camera).zoom(zoom).build()), RECENTER_ANIMATION_MILLIS)
    }

    fun updateEditorCamera(request: EditorCameraRequest?, bottomPadding: Int?, onApplied: (Long) -> Unit) {
        editorRequest = request
        editorBottomPadding = bottomPadding
        scheduleMapDetails()
        onEditorCameraApplied = onApplied
        fitEditorIfRequested()
    }

    private fun fitEditorIfRequested() {
        val request = editorRequest ?: return
        val requestedBottom = editorBottomPadding ?: return
        val native = map ?: return
        val view = mapView ?: return
        if (style == null || view.width == 0 || view.height == 0 || request.id == lastEditorRequestId) return
        val bottom = requestedBottom.coerceIn(0, view.height - 1)
        val points = request.points.distinct()
        if (points.isEmpty()) return
        val visibleHeight = (view.height - bottom).coerceAtLeast(1)
        val margin = routePaddingPx.coerceAtMost(visibleHeight / 4).coerceAtMost(view.width / 4)
        val update = if (points.size == 1) {
            CameraUpdateFactory.newLatLngZoom(points.single().toLatLng(), PLAN_POINT_ZOOM)
        } else {
            val bounds = LatLngBounds.Builder().apply { points.forEach { include(it.toLatLng()) } }.build()
            CameraUpdateFactory.newLatLngBounds(bounds, margin, margin, margin, bottom + margin)
        }
        native.moveCamera(update)
        // MapLibre keeps bounds padding on the camera. Remove it while preserving the
        // physical viewport center: the crosshair and subsequent pan/zoom use that center,
        // and a restored camera must not depend on the previous sheet dimensions.
        val fitted = native.cameraPosition
        val physicalCenter = native.projection.fromScreenLocation(PointF(view.width / 2f, view.height / 2f))
        if (fitted.padding?.any { it != 0.0 } == true) {
            native.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(fitted)
                .target(physicalCenter).padding(0.0, 0.0, 0.0, 0.0).build()))
        }
        lastEditorRequestId = request.id
        saveCamera()
        onEditorCameraApplied(request.id)
    }

    private val shieldSpanPolicy = ShieldSpanPolicy()
    private val detailVisibility = mutableMapOf<String, Boolean>()
    private var detailUpdatePending = false
    private val detailUpdate = Runnable {
        detailUpdatePending = false
        updateMapDetails()
    }
    private fun scheduleMapDetails() {
        if (detailUpdatePending) return
        val view = mapView ?: return
        detailUpdatePending = true
        view.postDelayed(detailUpdate, 75)
    }
    private fun updateMapDetails() {
        val native = map ?: return
        val view = mapView ?: return
        val loaded = style ?: return
        val rect = visibleViewport().rect
        val span = visibleSpan()
        val sampleWidth = (100f * view.resources.displayMetrics.density).coerceAtMost(rect.width * 0.6f)
        if (sampleWidth > 0f) {
            val left = native.projection.fromScreenLocation(PointF(rect.centerX - sampleWidth / 2f, rect.centerY))
            val right = native.projection.fromScreenLocation(PointF(rect.centerX + sampleWidth / 2f, rect.centerY))
            val distance = VisibleMapSpanCalculator.distance(
                GeoPoint(left.latitude, left.longitude), GeoPoint(right.latitude, right.longitude))
            val density = view.resources.displayMetrics.density
            val next = ScaleRulerPolicy.choose(distance / sampleWidth, 80f * density, 140f * density, lastRuler)
            if (next != lastRuler) { lastRuler = next; onRulerChanged(next) }
        }
        val shields = shieldSpanPolicy.update(span)
        loaded.layers.forEach { layer ->
            val show = when {
                layer.id.startsWith("route-shield-") -> shields
                layer.id.startsWith("facility-junction-") -> span.isFinite() && span in 0.0..5000.0
                layer.id.startsWith("facility-access-") -> span.isFinite() && span in 0.0..3000.0
                layer.id.startsWith("facility-toll-") -> span.isFinite() && span in 0.0..2000.0
                layer.id == "intersection-major" -> span.isFinite() && span in 0.0..3000.0
                layer.id == "intersection-normal" -> span.isFinite() && span in 0.0..1500.0
                else -> return@forEach
            }
            if (detailVisibility[layer.id] != show) {
                layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(
                    if (show) Property.VISIBLE else Property.NONE))
                detailVisibility[layer.id] = show
            }
        }
    }

    private var readyDelivered = false
    private val styleLoadedListener = MapView.OnDidFinishLoadingStyleListener {
        map?.style?.let { loadedStyle ->
            style = loadedStyle
            detailVisibility.clear()
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
        override fun onMoveBegin(detector: MoveGestureDetector) {
            gestureSuspended = true
            onGesture()
        }
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
        mapView.addOnLayoutChangeListener(layoutListener)
        mapView.addOnDidFinishLoadingStyleListener(styleLoadedListener)
        mapView.addOnDidFailLoadingMapListener(mapLoadFailedListener)
        mapView.getMapAsync { mapLibreMap ->
            map = mapLibreMap
            mapLibreMap.uiSettings.isCompassEnabled = false
            mapLibreMap.uiSettings.isTiltGesturesEnabled = false
            mapLibreMap.addOnMoveListener(moveListener)
            mapLibreMap.addOnCameraMoveStartedListener(cameraStartedListener)
            mapLibreMap.addOnCameraMoveListener(cameraListener)
            if (onLongPress != null) mapLibreMap.addOnMapLongClickListener(longClickListener)
            mapLibreMap.moveCamera(
                initialCamera?.let { camera ->
                    CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(camera.center.toLatLng())
                        .zoom(camera.zoom).bearing(camera.bearing).tilt(camera.tilt).build())
                } ?: CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM),
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
            mapView?.context?.let { JapaneseRoadShields.install(it, loadedStyle) }
            routeOverlay.setRoute(latestRoute)
            routeOverlay.install(loadedStyle)
            detourOverlay.install(loadedStyle)
            trafficOverlay.install(loadedStyle)
            routePlanOverlay.setRoutePlan(latestRoutePlan)
            routePlanOverlay.install(loadedStyle)
            installVehicleLayer(loadedStyle)
            OverlayLayerOrder.restore(loadedStyle)
            updateMapDetails()
            latestLocation?.let(::renderLocation)
            fitRouteIfRequested()
            fitRoutePlanIfRequested()
            fitEditorIfRequested()
        }.onFailure { error ->
            Log.e(TAG, "Unable to install map overlays", error)
        }
    }

    fun update(location: LocationState?, isFollowing: Boolean, recenterRequestId: Int,
        cameraState: NavigationCameraState = NavigationCameraState(), bottomOcclusionPx: Int = 0) {
        val previous = navigationCamera
        navigationCamera = cameraState.copy(following = isFollowing)
        if (navigationBottomOcclusionPx != bottomOcclusionPx) {
            navigationBottomOcclusionPx = bottomOcclusionPx
            scheduleMapDetails()
        }
        if (!cameraState.active) navigationInitialized = false
        val recenter = recenterRequestId != lastRecenterRequestId
        if (recenter || (!previous.following && isFollowing)) gestureSuspended = false
        val fresh = !cameraState.active || headingFreshness.isFresh(location, android.os.SystemClock.elapsedRealtime())
        if (location == null || !fresh) {
            if (cameraState.active && isFollowing && !gestureSuspended) map?.cancelTransitions()
            return
        }
        if (!shouldApplyMapFrame(latestLocation?.elapsedRealtimeMillis, location.elapsedRealtimeMillis)) return
        latestLocation = location
        val native = map
        if (native == null || !isFollowing || gestureSuspended) {
            renderLocation(location)
            return
        }
        val changed = location.timestampMillis != lastFollowedTimestampMillis ||
            location.elapsedRealtimeMillis != lastFollowedElapsedMillis
        val initialNavigationFollow = navigationCamera.active && !navigationInitialized
        val expectedBottomPadding = if (navigationCamera.active && navigationCamera.orientation == NavigationMapOrientation.HEADING_UP)
            bottomOcclusionPx else 0
        if (initialNavigationFollow || !hasCenteredOnFirstLocation || recenter || changed || previous != navigationCamera ||
            (navigationCamera.active && (native.cameraPosition.padding?.getOrNull(3)?.toInt() ?: 0) != expectedBottomPadding)) {
            val camera = native.cameraPosition
            val frame = navigationMapFrame(location, navigationCamera, camera.bearing,
                mapView?.height ?: 0, bottomOcclusionPx)
            val next = CameraPosition.Builder(camera)
                .target(frame.cameraTarget.toLatLng())
                .bearing(if (previous.active && !navigationCamera.active) 0.0 else frame.cameraBearing)
                .tilt(0.0)
                .zoom(if (initialNavigationFollow || (!hasCenteredOnFirstLocation && initialCamera == null)) FOLLOW_ZOOM else camera.zoom)
                .padding(0.0, frame.topPaddingPx, 0.0, frame.bottomPaddingPx)
                .build()
            native.cancelTransitions()
            if (navigationCamera.active) {
                // A fix owns the marker and camera in the same UI frame. An older ease
                // transition must never chase the next fix.
                native.moveCamera(CameraUpdateFactory.newCameraPosition(next))
                renderLocation(location, frame.markerScreenRotation)
                navigationInitialized = true
                onNavigationCameraInitialized()
            } else {
                renderLocation(location)
                native.easeCamera(CameraUpdateFactory.newCameraPosition(next), RECENTER_ANIMATION_MILLIS)
            }
            hasCenteredOnFirstLocation = true
            lastRecenterRequestId = recenterRequestId
            lastFollowedTimestampMillis = location.timestampMillis
            lastFollowedElapsedMillis = location.elapsedRealtimeMillis
        } else {
            renderLocation(location)
        }
    }

    fun updateRoute(route: ScheduledRoute?, routeOverviewRequestId: Int) {
        val routeChanged = route != latestRoute
        latestRoute = route
        latestRouteOverviewRequestId = routeOverviewRequestId
        // Preview requests are already consumed when navigation starts; do not refit over follow.
        if (navigationCamera.active && navigationCamera.following) lastRouteOverviewRequestId = routeOverviewRequestId
        routeOverlay.setRoute(route)
        if (routeChanged) {
            style?.let { loadedStyle ->
                runCatching { routeOverlay.render(loadedStyle) }
                    .onFailure { error -> Log.e(TAG, "Unable to render scheduled route", error) }
            }
        }
        fitRouteIfRequested()
    }

    fun updateTraffic(events: List<net.nobu0707.busnav.domain.traffic.TrafficEvent>) {
        if (trafficOverlay.events == events) return
        trafficOverlay.events = events
        style?.let { trafficOverlay.render(it) }
    }

    fun updateDetour(data: DetourOverlayData) {
        if (detourOverlay.data == data) return
        detourOverlay.data = data
        style?.let { detourOverlay.render(it) }
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
        mapView?.removeCallbacks(detailUpdate)
        detailUpdatePending = false
        saveCamera()
        mapView?.removeOnLayoutChangeListener(layoutListener)
        map?.removeOnCameraMoveListener(cameraListener)
        mapView?.removeOnDidFinishLoadingStyleListener(styleLoadedListener)
        mapView?.removeOnDidFailLoadingMapListener(mapLoadFailedListener)
        map?.removeOnMoveListener(moveListener)
        map?.removeOnCameraMoveStartedListener(cameraStartedListener)
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
                    org.maplibre.android.style.layers.PropertyFactory.iconSize(1f),
                    org.maplibre.android.style.layers.PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                    iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                ),
            )
        }
    }

    fun updateDeviceHeading(heading: Double?) {
        deviceHeadingDegrees = heading
        if (!navigationCamera.active) latestLocation?.let(::renderVehicleRotation)
    }

    private fun renderLocation(location: LocationState, markerRotation: Double? = null) {
        val loadedStyle = style ?: return
        (loadedStyle.getSource(VEHICLE_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
            Feature.fromGeometry(Point.fromLngLat(location.point.longitude, location.point.latitude)),
        )
        if (markerRotation != null) style?.getLayer(VEHICLE_LAYER_ID)?.setProperties(iconRotate(markerRotation.toFloat()))
        else renderVehicleRotation(location)
    }

    private fun renderVehicleRotation(location: LocationState) {
        val camera = map?.cameraPosition ?: return
        style?.getLayer(VEHICLE_LAYER_ID)?.setProperties(
            iconRotate(navigationCamera.copy(following = navigationCamera.following && !gestureSuspended)
                .vehicleScreenRotation(camera.bearing,
                    if (navigationCamera.active) location.normalizedBearingDegrees?.toDouble()
                    else deviceHeadingDegrees ?: location.normalizedBearingDegrees?.toDouble()).toFloat()),
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
        val geometry = VehicleMarkerGeometry()
        val density = mapView?.resources?.displayMetrics?.density ?: 1f
        val pixels = (geometry.size * density).toInt().coerceAtLeast(1)
        val bitmap = createBitmap(pixels, pixels).apply { this.density = (density * 160).toInt() }
        val canvas = Canvas(bitmap)
        canvas.scale(pixels / geometry.size, pixels / geometry.size)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = geometry.center
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        paint.color = Color.rgb(20, 35, 50)
        canvas.drawCircle(center.x, center.y, geometry.radius, paint)
        paint.strokeWidth = 5f
        paint.color = Color.WHITE
        canvas.drawCircle(center.x, center.y, geometry.radius, paint)
        paint.strokeWidth = 2.5f
        paint.color = Color.rgb(190, 25, 58)
        canvas.drawCircle(center.x, center.y, geometry.radius, paint)
        val arrow = Path().apply {
            moveTo(geometry.arrow.first().x, geometry.arrow.first().y)
            geometry.arrow.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 3f
        paint.color = Color.WHITE
        canvas.drawPath(arrow, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(190, 25, 58)
        canvas.drawPath(arrow, paint)
        return bitmap
    }

    private companion object {
        const val VEHICLE_SOURCE_ID = "busnav-vehicle-source"
        const val VEHICLE_LAYER_ID = OverlayLayerOrder.VEHICLE
        const val VEHICLE_ICON_ID = "busnav-vehicle-icon"
        const val DEFAULT_ZOOM = 4.5
        const val FOLLOW_ZOOM = 16.5
        const val PLAN_POINT_ZOOM = 15.0

        const val RECENTER_ANIMATION_MILLIS = 750
        const val TAG = "BusNavMapController"
        val DEFAULT_LOCATION = LatLng(36.2048, 138.2529)
    }
}
