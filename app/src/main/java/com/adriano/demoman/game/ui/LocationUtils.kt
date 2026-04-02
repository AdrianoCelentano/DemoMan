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

fun List<LatLng>.orderClockwise(): List<LatLng> {
    if (size != 4) return this // Logic designed for quadrilaterals

    // 1. Sort by Latitude descending (Top to Bottom)
    val sortedByLat = this.sortedByDescending { it.latitude }

    // 2. Take the two highest points and sort them by Longitude
    val topTwo = sortedByLat.take(2).sortedBy { it.longitude }
    val topLeft = topTwo[0]
    val topRight = topTwo[1]

    // 3. Take the two lowest points and sort them by Longitude (descending for clockwise)
    val bottomTwo = sortedByLat.takeLast(2).sortedByDescending { it.longitude }
    val bottomRight = bottomTwo[0]
    val bottomLeft = bottomTwo[1]

    return listOf(topLeft, topRight, bottomRight, bottomLeft)
}
