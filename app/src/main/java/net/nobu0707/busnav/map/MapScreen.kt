package net.nobu0707.busnav.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.nobu0707.busnav.location.LocationState
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.domain.routeplan.RoutePlan
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.BuildConfig
import net.nobu0707.busnav.map.basemap.AndroidLogMapDiagnostics
import net.nobu0707.busnav.map.basemap.BasemapAttribution
import net.nobu0707.busnav.map.basemap.BasemapConfig
import net.nobu0707.busnav.map.basemap.BasemapMode
import net.nobu0707.busnav.map.basemap.BasemapState
import net.nobu0707.busnav.map.basemap.NoOpMapDiagnostics
import net.nobu0707.busnav.ui.routeplan.EditorCamera
import net.nobu0707.busnav.ui.routeplan.EditorCameraRequest
import org.maplibre.android.maps.MapView

val LocalNavigationMapBottomOcclusionPx = compositionLocalOf { 0 }
val LocalNavigationMapTopOverlayPx = compositionLocalOf { 0 }

@Composable
fun MapScreen(
    location: LocationState?,
    isFollowingLocation: Boolean,
    recenterRequestId: Int,
    activeRoute: ScheduledRoute?,
    routeOverviewRequestId: Int,
    modifier: Modifier = Modifier,
    routePlan: RoutePlan? = null,
    detourOverlay: DetourOverlayData = DetourOverlayData(),
    trafficEvents: List<net.nobu0707.busnav.domain.traffic.TrafficEvent> = emptyList(),
    planOverviewRequestId: Int = 0,
    onMapLongPress: ((GeoPoint) -> Unit)? = null,
    basemapConfig: BasemapConfig = BasemapConfig.fromBuildValue(
        styleUrl = BuildConfig.BASEMAP_STYLE_URL,
        isDebug = BuildConfig.DEBUG,
    ),
    navigationCamera: net.nobu0707.busnav.domain.navigation.NavigationCameraState = net.nobu0707.busnav.domain.navigation.NavigationCameraState(),
    deviceHeadingOverride: Double? = null,
    deviceCompassEnabled: Boolean = !navigationCamera.active && deviceHeadingOverride == null,
    onToggleOrientation: (() -> Unit)? = null,
    initialCamera: EditorCamera? = null,
    initialNavigationCamera: Boolean = navigationCamera.active && initialCamera != null,
    onNavigationCameraInitialized: () -> Unit = {},
    onCameraChanged: (EditorCamera) -> Unit = {},
    editorCameraRequest: EditorCameraRequest? = null,
    editorBottomPadding: Int? = null,
    onEditorCameraApplied: (Long) -> Unit = {},
    onCursorReader: (((() -> GeoPoint?)?) -> Unit) = {},
    selectionMode: MapSelectionMode = if (onMapLongPress != null) MapSelectionMode.ROUTE_POINT else MapSelectionMode.NONE,
    monitorTunnel: Boolean = false,
    onTunnelChanged: (Boolean) -> Unit = {},
    onMapReady: () -> Unit,
    onMapGesture: () -> Unit,
    onMapError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val routePaddingPx = with(LocalDensity.current) { 64.dp.roundToPx() }
    val bottomOcclusionPx = LocalNavigationMapBottomOcclusionPx.current
    val topOverlayPx = LocalNavigationMapTopOverlayPx.current
    var basemapState by remember {
        mutableStateOf(
            if (basemapConfig.mode == BasemapMode.FALLBACK) {
                BasemapState.UNAVAILABLE
            } else {
                BasemapState.LOADING
            },
        )
    }
    val mapDiagnostics = remember {
        if (BuildConfig.DEBUG) AndroidLogMapDiagnostics() else NoOpMapDiagnostics
    }
    val mapView = remember(context) {
        MapView(context).also {
            it.layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            it.onCreate(null)
        }
    }
    var cameraBearing by remember { mutableStateOf(initialCamera?.bearing ?: 0.0) }
    var ruler by remember { mutableStateOf<ScaleRulerReading?>(null) }
    var deviceHeading by remember { mutableStateOf<Double?>(null) }
    val sensorLocation by rememberUpdatedState(location)
    val cameraCallback by androidx.compose.runtime.rememberUpdatedState(onCameraChanged)
    val initializedCallback by androidx.compose.runtime.rememberUpdatedState(onNavigationCameraInitialized)
    val controller = remember(mapView, routePaddingPx) {
        MapController(
            initialCamera = initialCamera,
            initialNavigationCamera = initialNavigationCamera,
            onNavigationCameraInitialized = { initializedCallback() },
            onCameraChanged = { camera -> cameraBearing = camera.bearing; cameraCallback(camera) },
            onRulerChanged = { ruler = it },
            onReady = onMapReady,
            onGesture = onMapGesture,
            onError = onMapError,
            onLongPress = onMapLongPress,
            routePaddingPx = routePaddingPx,
            basemapConfig = basemapConfig,
            mapDiagnostics = mapDiagnostics,
            onBasemapStateChanged = { basemapState = it },
        ).also { it.attach(mapView) }
    }

    DisposableEffect(controller, selectionMode) {
        onCursorReader(if (selectionMode != MapSelectionMode.NONE) controller::cursorPosition else null)
        onDispose { onCursorReader(null) }
    }

    val tunnelCallback by androidx.compose.runtime.rememberUpdatedState(onTunnelChanged)
    androidx.compose.runtime.LaunchedEffect(controller, monitorTunnel) {
        if (!monitorTunnel) {
            controller.resetTunnel()
            tunnelCallback(false)
        } else {
            while (true) {
                tunnelCallback(controller.sampleTunnel())
                kotlinx.coroutines.delay(1_000)
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        var started = false
        var resumed = false

        fun startIfNeeded() {
            if (!started) {
                mapView.onStart()
                started = true
            }
        }

        fun resumeIfNeeded() {
            startIfNeeded()
            if (!resumed) {
                mapView.onResume()
                resumed = true
            }
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) startIfNeeded()
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resumeIfNeeded()

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> startIfNeeded()
                Lifecycle.Event.ON_RESUME -> resumeIfNeeded()
                Lifecycle.Event.ON_PAUSE -> if (resumed) {
                    mapView.onPause()
                    resumed = false
                }
                Lifecycle.Event.ON_STOP -> if (started) {
                    mapView.onStop()
                    started = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            controller.detach()
            mapView.onDestroy()
        }
    }

    DisposableEffect(context, lifecycleOwner, mapView, deviceCompassEnabled) {
        val sensor = DeviceHeadingSensor(context, { sensorLocation },
            { mapView.display?.rotation ?: android.view.Surface.ROTATION_0 }) { deviceHeading = it }
        fun sync() {
            if (deviceCompassEnabled && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                sensor.start() else sensor.stop()
        }
        val observer = LifecycleEventObserver { _, _ -> sync() }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (!deviceCompassEnabled) deviceHeading = null
        sync()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); sensor.stop() }
    }

    SideEffect {
        controller.updateBasemap(basemapConfig)
        controller.updateDeviceHeading(deviceHeadingOverride ?: if (deviceCompassEnabled) deviceHeading else null)
        controller.update(location, isFollowingLocation, recenterRequestId, navigationCamera, bottomOcclusionPx, topOverlayPx)
        controller.updateRoute(activeRoute, routeOverviewRequestId)
        controller.updateDetour(detourOverlay)
        controller.updateTraffic(trafficEvents)
        controller.updateRoutePlan(routePlan, planOverviewRequestId)
        controller.updateEditorCamera(editorCameraRequest, editorBottomPadding, onEditorCameraApplied)
    }

    Box(
        modifier = modifier
            .testTag(BasemapTestTags.CONTAINER)
            .semantics { stateDescription = basemapState.name },
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )
        MapControls(navigationCamera, cameraBearing, onToggleOrientation, controller::resetNorth,
            controller::zoomBy, ruler, Modifier.align(Alignment.TopEnd)
                .padding(top = with(LocalDensity.current) { topOverlayPx.toDp() },
                    bottom = with(LocalDensity.current) { bottomOcclusionPx.toDp() }).padding(8.dp))
        BasemapStatusOverlay(
            failureHint = basemapConfig.failureHint,
            state = basemapState,
            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
        )
        BasemapAttributionOverlay(
            modifier = if (editorBottomPadding != null) Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 40.dp)
                else Modifier.align(Alignment.BottomStart).padding(6.dp),
        )
    }
}

object BasemapTestTags {
    const val CONTAINER = "basemap_container"
    const val UNAVAILABLE = "basemap_unavailable"
    const val ATTRIBUTION = "basemap_attribution"
}

@Composable
internal fun BasemapStatusOverlay(state: BasemapState, modifier: Modifier = Modifier, failureHint: String? = null) {
    if (state != BasemapState.UNAVAILABLE) return
    Card(
        modifier = modifier.testTag(BasemapTestTags.UNAVAILABLE),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
    ) {
        Text(
            text = "\u8a73\u7d30\u5730\u56f3\u30b5\u30fc\u30d0\u30fc\u672a\u63a5\u7d9a" +
                (failureHint?.let { "\n$it" } ?: ""),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun BasemapAttributionOverlay(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.testTag(BasemapTestTags.ATTRIBUTION),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        ),
    ) {
        Text(
            text = BasemapAttribution.VISIBLE_TEXT,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
