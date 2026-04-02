package com.adriano.demoman.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.adriano.demoman.R
import com.adriano.demoman.game.domain.DebugViewState
import com.adriano.demoman.game.domain.GameEvent
import com.adriano.demoman.game.domain.GameSession
import com.adriano.demoman.game.domain.Team
import com.adriano.demoman.game.domain.Tower
import com.adriano.demoman.ui.theme.DemoManTheme
import com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
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
    game: GameSession,
    remainingTime: Long?,
    debugState: DebugViewState? = null
) {
    var permissionRequestCount by remember { mutableIntStateOf(0) }
    val hasLocationPermission = hasLocationPermission(permissionRequestCount)

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            onEvent(GameEvent.ObserveLocation)
            onEvent(GameEvent.StartGameTimer)
        }
    }
    ExitGameDialog(onEvent)

    if (hasLocationPermission) {
        GameMap(innerPadding, game, hasLocationPermission, remainingTime, debugState, onEvent)
    } else {
        LocationPermissionScreen(
            onRequestPermission = { permissionRequestCount++ }
        )
    }
}

@Composable
private fun GameMap(
    innerPadding: PaddingValues,
    game: GameSession,
    hasLocationPermission: Boolean,
    remainingTime: Long?,
    debugState: DebugViewState?,
    onEvent: (GameEvent) -> Unit
) {
    LifecycleResumeEffect(Unit) {
        onEvent(GameEvent.ObserveGameState)
        onPauseOrDispose {
            onEvent(GameEvent.StopObservingGameState)
        }
    }
    
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
                        durationMs = 1000
                    )
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

        // Role title at the top center
        Text(
            text = if (game.role == Team.DETECTIVE) "AGENT" else "DEMOMAN",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Black,
                color = if (game.role == Team.DETECTIVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
        )

        // Timer at the bottom center
        remainingTime?.let {
            Text(
                text = formatTime(it),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        if (debugState != null && game.role == Team.MISTER_X) {
            DebugOverlay(debugState)
        }
    }
}

private fun formatTime(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(minutes, secs)
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

    BackHandler { showEndGameDialog = true }
}

@Preview(showBackground = true)
@Composable
fun GameMapScreenAgentPreview() {
    DemoManTheme {
        GameMapScreen(
            innerPadding = PaddingValues(0.dp),
            onEvent = {},
            game = GameSession(
                id = "Test",
                role = Team.DETECTIVE,
                playground = listOf(LatLng(49.0, 8.0), LatLng(49.1, 8.1))
            ),
            remainingTime = 3599L
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GameMapScreenDemomanPreview() {
    DemoManTheme {
        GameMapScreen(
            innerPadding = PaddingValues(0.dp),
            onEvent = {},
            game = GameSession(
                id = "Test",
                role = Team.MISTER_X,
                playground = listOf(LatLng(49.0, 8.0), LatLng(49.1, 8.1))
            ),
            remainingTime = 120L
        )
    }
}
