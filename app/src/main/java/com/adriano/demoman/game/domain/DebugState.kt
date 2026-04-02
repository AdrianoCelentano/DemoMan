package com.adriano.demoman.game.domain

import android.location.Location
import com.google.android.gms.maps.model.LatLng

data class DebugViewState(
    val playerPosition: LatLng? = null,
    val towerDistances: List<TowerDistance> = emptyList()
)

data class TowerDistance(
    val towerPosition: LatLng,
    val distanceMeters: Float
)

fun calculateDebugState(playerPosition: LatLng, towers: List<Tower>): DebugViewState {
    val distances = towers.map { tower ->
        val results = FloatArray(1)
        Location.distanceBetween(
            playerPosition.latitude,
            playerPosition.longitude,
            tower.position.latitude,
            tower.position.longitude,
            results
        )
        TowerDistance(tower.position, results[0])
    }
    return DebugViewState(playerPosition, distances)
}
