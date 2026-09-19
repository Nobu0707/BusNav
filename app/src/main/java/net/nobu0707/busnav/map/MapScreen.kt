package net.nobu0707.busnav.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.nobu0707.busnav.location.LocationState
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.domain.routeplan.RoutePlan
import net.nobu0707.busnav.domain.model.GeoPoint
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
    onMapReady: () -> Unit,
    onMapGesture: () -> Unit,
    onMapError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val routePaddingPx = with(LocalDensity.current) { 64.dp.roundToPx() }
    val mapView = remember(context) {
        MapView(context).also { it.onCreate(null) }
    }
    val controller = remember {
        MapController(
            onReady = onMapReady,
            onGesture = onMapGesture,
            onError = onMapError,
            onLongPress = onMapLongPress,
            routePaddingPx = routePaddingPx,
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
        controller.update(location, isFollowingLocation, recenterRequestId)
        controller.updateRoute(activeRoute, routeOverviewRequestId)
        controller.updateRoutePlan(routePlan, planOverviewRequestId)
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
