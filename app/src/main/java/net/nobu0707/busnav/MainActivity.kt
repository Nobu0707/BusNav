package net.nobu0707.busnav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        setContent {
            BusNavTheme {
                NavigationRoute(locationProvider = locationProvider)
            }
        }
    }
}
