package net.nobu0707.busnav

import android.os.Bundle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.first
import net.nobu0707.busnav.developer.createConnectionRepository
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.nobu0707.busnav.data.route.InMemoryScheduledRouteRepository
import net.nobu0707.busnav.data.routing.valhalla.AndroidLogRoutingDiagnostics
import net.nobu0707.busnav.data.routing.valhalla.NoOpRoutingDiagnostics
import net.nobu0707.busnav.data.routing.valhalla.RoutingConfig
import net.nobu0707.busnav.data.routing.valhalla.ValhallaRoutingEngine
import net.nobu0707.busnav.domain.route.ScheduledRouteRepository
import net.nobu0707.busnav.location.AndroidLocationProvider
import net.nobu0707.busnav.ui.navigation.NavigationRoute
import net.nobu0707.busnav.ui.theme.BusNavTheme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(applicationContext)
        enableEdgeToEdge()
        val locationProvider = AndroidLocationProvider(applicationContext)
        val routeRepository: ScheduledRouteRepository = InMemoryScheduledRouteRepository(
            activeRoute = if (BuildConfig.DEBUG) {
                net.nobu0707.busnav.data.route.createDevelopmentSampleRoute()
            } else {
                null
            },
        )
        val diagnostics = if (BuildConfig.DEBUG) {
            AndroidLogRoutingDiagnostics()
        } else {
            NoOpRoutingDiagnostics
        }
        val connections = createConnectionRepository(applicationContext)
        val routingEngine = ValhallaRoutingEngine(
            config = RoutingConfig(BuildConfig.VALHALLA_BASE_URL),
            diagnostics = diagnostics,
            baseUrlProvider = { connections.settings.first().valhallaBaseUrl },
        )
        setContent {
            val settings by connections.settings.collectAsState(initial = null)
            BusNavTheme {
                val effective = settings ?: return@BusNavTheme
                NavigationRoute(
                    locationProvider = locationProvider,
                    routeRepository = routeRepository,
                    routingEngine = routingEngine,
                    connectionRepository = connections,
                    basemapConfig = effective.basemapConfig(BuildConfig.DEBUG),
                )
            }
        }
    }
}
