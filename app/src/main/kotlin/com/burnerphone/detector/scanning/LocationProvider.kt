package com.burnerphone.detector.scanning

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Provides current location for device detection events
 */
class LocationProvider(context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(useCoarseLocation: Boolean = false): LocationData? {
        return try {
            val priority = if (useCoarseLocation) {
                Priority.PRIORITY_LOW_POWER  // Coarse location for battery saving
            } else {
                Priority.PRIORITY_HIGH_ACCURACY  // Accurate location for tracking
            }

            val location = fusedLocationClient.getCurrentLocation(
                priority,
                null
            ).await()

            location?.toLocationData()
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun Location.toLocationData() = LocationData(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = time
    )
}
