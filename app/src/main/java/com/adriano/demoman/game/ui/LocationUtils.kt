package com.adriano.demoman.game.ui

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

fun createOuterBounds(playground: List<LatLng>): List<LatLng> {
    if (playground.isEmpty()) return emptyList()

    val minLat = playground.minOf { it.latitude }
    val maxLat = playground.maxOf { it.latitude }
    val minLng = playground.minOf { it.longitude }
    val maxLng = playground.maxOf { it.longitude }

//    val offset = 0.0045
    val offset = 0.05

    return listOf(
        LatLng(maxLat + offset, minLng - offset), // Top Left
        LatLng(maxLat + offset, maxLng + offset), // Top Right
        LatLng(minLat - offset, maxLng + offset), // Bottom Right
        LatLng(minLat - offset, minLng - offset)  // Bottom Left
    )
}

fun findCenter(points: List<LatLng>): LatLng {
    val builder = LatLngBounds.Builder()
    for (point in points) {
        builder.include(point)
    }
    return builder.build().center
}
