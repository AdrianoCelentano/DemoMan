package com.adriano.demoman.game.data

import com.google.android.gms.maps.model.LatLng

val centerPos = LatLng(49.08822215735542, 8.400129266259198)

val geofencePoints =
    listOf(
        LatLngDto(
            49.0905196956559,
            8.398798890587775
        ),
        LatLngDto(
            49.08709794084221,
            8.397082276818216
        ),
        LatLngDto(
            49.086219628993504,
            8.402210660454827
        ),
        LatLngDto(
            49.08924798269771,
            8.40284366178234
        )
    )

private val mapRadius = 0.008 // Roughly 200m
val worldBounds =
    listOf(
        LatLng(
            centerPos.latitude - mapRadius,
            centerPos.longitude - mapRadius
        ),
        LatLng(
            centerPos.latitude + mapRadius,
            centerPos.longitude - mapRadius
        ),
        LatLng(
            centerPos.latitude + mapRadius,
            centerPos.longitude + mapRadius
        ),
        LatLng(
            centerPos.latitude - mapRadius,
            centerPos.longitude + mapRadius
        )
    )

//val towers = listOf()