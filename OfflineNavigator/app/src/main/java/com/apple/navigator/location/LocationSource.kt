package com.apple.navigator.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.provider.ProviderRequest
import android.os.Handler
import android.os.Looper

data class Fix(
    val lat: Double,
    val lon: Double,
    val speedMps: Double,
    val bearingDegrees: Float?,
)

/**
 * Thin wrapper around the platform LocationManager: gives us GPS fixes with
 * speed and bearing with no Google Play dependency. Receives fixes from GPS
 * and network providers, prefers freshest.
 */
class LocationSource(
    context: Context,
    private val onFix: (Fix) -> Unit,
) : LocationListener {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var started = false

    @SuppressLint("MissingPermission")
    fun start() {
        if (started) return
        started = true
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 0f, this, handler.looper
            )
        } catch (_: Exception) {
        }
        try {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 4000L, 0f, this, handler.looper
            )
        } catch (_: Exception) {
        }
        // immediate first fix
        try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onLocationChanged(it) }
        } catch (_: Exception) {
        }
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            locationManager.removeUpdates(this)
        } catch (_: Exception) {
        }
    }

    override fun onLocationChanged(location: Location) {
        onFix(
            Fix(
                lat = location.latitude,
                lon = location.longitude,
                speedMps = location.speed.toDouble(),
                bearingDegrees = if (location.hasBearing()) location.bearing.toFloat() else null,
            )
        )
    }

    fun lastKnown(): Fix? {
        val gps = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            null
        }
        val network = try {
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            null
        }
        val best = listOfNotNull(gps, network).maxByOrNull { it.time } ?: return null
        return Fix(best.latitude, best.longitude, best.speed.toDouble(), if (best.hasBearing()) best.bearing else null)
    }

    // deprecated callback kept for potential older flows
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
}
