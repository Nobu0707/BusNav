package net.nobu0707.busnav.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import org.maplibre.android.maps.MapView

@Composable
fun MapScreen(
    location: LocationState?,
    isFollowingLocation: Boolean,
    recenterRequestId: Int,
    activeRoute: ScheduledRoute?,
    routeOverviewRequestId: Int,
    modifier: Modifier = Modifier,
    routePlan: RoutePlan? = null,
    planOverviewRequestId: Int = 0,
    onMapLongPress: ((GeoPoint) -> Unit)? = null,
    basemapConfig: BasemapConfig = BasemapConfig.fromBuildValue(
        styleUrl = BuildConfig.BASEMAP_STYLE_URL,
        isDebug = BuildConfig.DEBUG,
    ),
    onMapReady: () -> Unit,
    onMapGesture: () -> Unit,
    onMapError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val routePaddingPx = with(LocalDensity.current) { 64.dp.roundToPx() }
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
        MapView(context).also { it.onCreate(null) }
    }
    val controller = remember(mapView, routePaddingPx) {
        MapController(
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

    SideEffect {
        controller.updateBasemap(basemapConfig)
        controller.update(location, isFollowingLocation, recenterRequestId)
        controller.updateRoute(activeRoute, routeOverviewRequestId)
        controller.updateRoutePlan(routePlan, planOverviewRequestId)
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
        BasemapStatusOverlay(
            state = basemapState,
            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
        )
        BasemapAttributionOverlay(
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
        )
    }
}

object BasemapTestTags {
    const val CONTAINER = "basemap_container"
    const val UNAVAILABLE = "basemap_unavailable"
    const val ATTRIBUTION = "basemap_attribution"
}

@Composable
internal fun BasemapStatusOverlay(state: BasemapState, modifier: Modifier = Modifier) {
    if (state != BasemapState.UNAVAILABLE) return
    Card(
        modifier = modifier.testTag(BasemapTestTags.UNAVAILABLE),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
    ) {
        Text(
            text = "\u8a73\u7d30\u5730\u56f3\u30b5\u30fc\u30d0\u30fc\u672a\u63a5\u7d9a",
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
