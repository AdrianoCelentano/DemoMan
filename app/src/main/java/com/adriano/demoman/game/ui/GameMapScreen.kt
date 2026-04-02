package com.adriano.demoman.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.adriano.demoman.R
import com.adriano.demoman.game.domain.GameEvent
import com.adriano.demoman.game.domain.GameSession
import com.adriano.demoman.game.domain.Tower
import com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.launch

@Composable
fun GameMapScreen(
    innerPadding: PaddingValues,
    onEvent: (GameEvent) -> Unit,
    game: GameSession
) {
    val mapLoaded = remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (mapLoaded.value) 1f else 0f,
        animationSpec = tween(
            durationMillis = 2000,
            delayMillis = 500,
            easing = FastOutSlowInEasing
        ),
        label = "MarkerScale"
    )

    val hasLocationPermission = hasLocationPermission()
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) onEvent(GameEvent.ObserveLocation)
    }
    ExitGameDialog(onEvent)

    Box(
        modifier = Modifier.padding(innerPadding)
    ) {
        val context = LocalContext.current
        val centerPos = remember { findCenter(game.playground) }
        val bounds = remember { createOuterBounds(game.playground) }

        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition(centerPos, 13f, 0f, 10f)
        }

        val scope = rememberCoroutineScope()

        val mapStyleOptions = remember {
            MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style)
        }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapStyleOptions = mapStyleOptions
            ),
            onMapLoaded = {
                val boundsBuilder = LatLngBounds.Builder()
                game.playground.forEach { boundsBuilder.include(it) }
                scope.launch {
                    cameraPositionState.animate(
                        update = newLatLngBounds(boundsBuilder.build(), 20),
                        durationMs = 1000)
                }
                mapLoaded.value = true
            },
            uiSettings = MapUiSettings(
                scrollGesturesEnabled = false,
                zoomGesturesEnabled = false,
                tiltGesturesEnabled = false,
                rotationGesturesEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = false,
                zoomControlsEnabled = false
            )
        ) {
            TowerMarker(game.towers, scale)
            Polygon(
                points = bounds,
                holes = listOf(game.playground),
                fillColor = MaterialTheme.colorScheme.background.copy(alpha = scale * 0.75f),
                strokeColor = MaterialTheme.colorScheme.background.copy(alpha = scale * 1f),
                strokeWidth = 4f
            )
        }
    }
}

@Composable
fun TowerMarker(towers: List<Tower>, scale: Float) {
    val context = LocalContext.current
    val towerIcon = remember {
        getResizedBitmap(context, R.drawable.tower, 104, 104)
    }
    val towerDownIcon = remember {
        getResizedBitmap(context, R.drawable.tower_down, 104, 104)
    }
    towers.forEach { tower ->
        Marker(
            alpha = scale,
            icon = if (tower.isActive) towerDownIcon else towerIcon,
            state = rememberUpdatedMarkerState(position = tower.position),
        )
    }
}

@Composable
private fun ExitGameDialog(onEvent: (GameEvent) -> Unit) {
    var showEndGameDialog by remember { mutableStateOf(false) }

    if (showEndGameDialog) {
        AlertDialog(
            onDismissRequest = { showEndGameDialog = false },
            title = { Text(text = "Spiel beenden?") },
            text = { Text(text = "Möchtest du das Spiel wirklich beenden?") },
            confirmButton = {
                TextButton(onClick = {
                    showEndGameDialog = false
                    onEvent(GameEvent.EndGame)
                }) {
                    Text("Beenden")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndGameDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    BackHandler() { showEndGameDialog = true }
}