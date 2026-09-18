package net.nobu0707.busnav.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import net.nobu0707.busnav.domain.model.GeoPoint

class AndroidLocationProvider(context: Context) : LocationProvider {
    private val locationManager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun isLocationEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }

    @SuppressLint("MissingPermission")
    override fun updates(): Flow<LocationUpdate> = callbackFlow {
        if (!isLocationEnabled()) {
            trySend(LocationUpdate.Disabled)
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(LocationUpdate.Position(location.toLocationState()))
            }

            override fun onProviderDisabled(provider: String) {
                if (!isLocationEnabled()) trySend(LocationUpdate.Disabled)
            }

            override fun onProviderEnabled(provider: String) = Unit

            @Deprecated("Required on API levels below 30")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter(locationManager.allProviders::contains)

        if (providers.isEmpty()) {
            trySend(LocationUpdate.Error("利用可能な位置情報プロバイダーがありません"))
            close()
            return@callbackFlow
        }

        try {
            providers.forEach { provider ->
                locationManager.getLastKnownLocation(provider)?.let {
                    trySend(LocationUpdate.Position(it.toLocationState()))
                }
                locationManager.requestLocationUpdates(
                    provider,
                    UPDATE_INTERVAL_MILLIS,
                    MIN_DISTANCE_METERS,
                    listener,
                    Looper.getMainLooper(),
                )
            }
        } catch (error: SecurityException) {
            trySend(LocationUpdate.Error("位置情報権限を確認してください"))
            close(error)
        } catch (error: RuntimeException) {
            trySend(LocationUpdate.Error(error.message ?: "位置情報を開始できませんでした"))
            close(error)
        }

        awaitClose { locationManager.removeUpdates(listener) }
    }

    private fun Location.toLocationState() = LocationState(
        point = GeoPoint(latitude, longitude),
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        bearingDegrees = if (hasBearing()) bearing else null,
        speedMetersPerSecond = if (hasSpeed()) speed else null,
        timestampMillis = time,
    )

    private companion object {
        const val UPDATE_INTERVAL_MILLIS = 1_000L
        const val MIN_DISTANCE_METERS = 1f
    }
}
