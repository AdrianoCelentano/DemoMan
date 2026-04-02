package com.adriano.demoman.game.ui

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.adriano.demoman.R
import com.adriano.demoman.game.domain.CreateGame
import com.adriano.demoman.game.domain.CreateGameStep
import com.adriano.demoman.game.domain.GameEvent
import com.adriano.demoman.game.domain.GameViewModel
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.rememberUpdatedMarkerState

@SuppressLint("MissingPermission")
@Composable
fun CreateGameScreen(innerPadding: PaddingValues, viewModel: GameViewModel = hiltViewModel()) {

    val state = viewModel.gameState.collectAsState().value.step
    if (state !is CreateGame) return

    BackHandler() { viewModel.onEvent(GameEvent.GoToSetup) }

    val scale by animateFloatAsState(
        targetValue = if (state.step != CreateGameStep.Boundary) 1f else 0f,
        animationSpec = tween(
            durationMillis = 2000,
            delayMillis = 500,
            easing = FastOutSlowInEasing
        ),
        label = "MarkerScale"
    )

    val context = LocalContext.current
    val hasLocationPermission = hasLocationPermission()
    val mapStyleOptions = remember {
        MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style)
    }
    val playground = remember(state.bounds) { createOuterBounds(state.bounds) }
    val cameraPositionState = rememberCameraPositionState()
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val location = viewModel.lastLocation()
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(
                        location.latitude,
                        location.longitude
                    ), 15f
                ), 1000
            )
        }
    }

    Box(modifier = Modifier.padding(innerPadding)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapStyleOptions = if (state.step != CreateGameStep.Boundary) mapStyleOptions else null
            ),
            uiSettings = MapUiSettings(),
            onMapClick = { position ->
                viewModel.onEvent(GameEvent.CreateGameMapClick(position))
            }
        ) {
            state.bounds.forEachIndexed { index, position ->
                Marker(
                    rememberUpdatedMarkerState(position),
                    onClick = { marker ->
                        viewModel.onEvent(GameEvent.CreateGameMapClick(marker.position))
                        true
                    }
                )
            }
            val towerBitmap = remember { getResizedBitmap(context, R.drawable.tower, 104, 104) }
            state.towers.forEach { position ->
                Marker(
                    state = rememberUpdatedMarkerState(position),
                    icon = towerBitmap,
                    onClick = { marker ->
                        viewModel.onEvent(GameEvent.CreateGameMapClick(marker.position))
                        true
                    }
                )
            }

            if (state.step != CreateGameStep.Boundary) {
                Polygon(
                    points = playground,
                    holes = listOf(state.bounds),
                    fillColor = MaterialTheme.colorScheme.background.copy(alpha = scale * 0.75f),
                    strokeColor = MaterialTheme.colorScheme.background.copy(alpha = scale),
                    strokeWidth = 4f
                )
            }

        }
    }

}