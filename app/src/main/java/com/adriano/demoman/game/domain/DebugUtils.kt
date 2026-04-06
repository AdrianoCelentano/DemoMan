package com.adriano.demoman.game.domain

import android.location.Location
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Creates a flow that simulates a walking route by emitting interpolated LatLng positions
 * between the provided waypoints at a given speed.
 *
 * @param route The list of LatLng waypoints forming the route.
 * @param speedKmh The average walking speed in km/h (default is 5.0 km/h).
 * @param updateIntervalMs The delay between each emitted location in milliseconds (default is 1000ms).
 */
fun simulateWalkingRoute(
    route: List<LatLng>,
    speedKmh: Float = 5.0f,
    updateIntervalMs: Long = 1000L
): Flow<LatLng> = flow {
    if (route.isEmpty()) return@flow

    emit(route.first()) // Emit the starting position immediately

    // Convert km/h to m/s
    val speedMpS = speedKmh * 1000f / 3600f

    // How many meters we move per interval delay
    val stepDistance = speedMpS * (updateIntervalMs / 1000f)

    for (i in 0 until route.size - 1) {
        val start = route[i]
        val end = route[i + 1]

        val results = FloatArray(1)
        Location.distanceBetween(
            start.latitude,
            start.longitude,
            end.latitude,
            end.longitude,
            results
        )
        val totalDistance = results[0]

        var distanceCovered = 0f

        while (distanceCovered < totalDistance) {
            delay(updateIntervalMs)
            distanceCovered += stepDistance

            if (distanceCovered >= totalDistance) {
                // We've reached or passed the waypoint, emit the exact waypoint to avoid overshooting
                emit(end)
            } else {
                // Simple linear interpolation (perfectly fine for short walking distances)
                val fraction = distanceCovered / totalDistance
                val interpLat = start.latitude + (end.latitude - start.latitude) * fraction
                val interpLng = start.longitude + (end.longitude - start.longitude) * fraction
                emit(LatLng(interpLat, interpLng))
            }
        }
    }
}