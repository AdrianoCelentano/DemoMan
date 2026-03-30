package com.adriano.demoman.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.adriano.demoman.game.GameEvent
import com.adriano.demoman.game.GameList
import com.adriano.demoman.game.GameSession
import com.adriano.demoman.game.GameStep
import com.adriano.demoman.game.GameViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

@Composable
fun GameScreen(innerPadding: PaddingValues, viewModel: GameViewModel = hiltViewModel()) {
    val state = viewModel.gameState.collectAsState().value
    val onEvent = viewModel::onEvent
    when (state.step) {
        is GameList -> GameListScreen(state.step.games, onEvent, innerPadding)
        GameStep.Game -> GameMap(innerPadding, onEvent)
        GameStep.Loading -> LoadingScreen()
        GameStep.Setup -> SetupScreen(onEvent, innerPadding)
    }
}

@Composable
fun SetupScreen(onEvent: (GameEvent) -> Unit, innerPadding: PaddingValues) {
    Column(modifier = Modifier.padding(innerPadding)) {
        Button(onClick = {
            onEvent(GameEvent.CreateGame)
        }) { Text(text = "Neues Spiel") }

        Button(onClick = {
            onEvent(GameEvent.GoToGameList)
        }) { Text(text = "Spiel beitreten") }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(
            modifier = Modifier.align(
                Alignment.Center
            )
        )
    }
}

@Composable
fun GameListScreen(
    games: List<GameSession>,
    onEvent: (GameEvent) -> Unit,
    innerPadding: PaddingValues
) {
    BackHandler() { onEvent(GameEvent.GoToSetup) }
    LazyColumn(modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)) {
        items(games) { game ->
            if (game.id == null) return@items
            Text(
                text = game.id, modifier = Modifier
                    .padding(8.dp)
                    .clickable {
                        onEvent(GameEvent.JoinGame(game.id))
                    })
        }
    }
}

@Composable
private fun GameMap(innerPadding: PaddingValues, onEvent: (GameEvent) -> Unit) {
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
    Box(
        modifier = Modifier.padding(innerPadding)
    ) {
        val centerPos = LatLng(49.08822215735542, 8.400129266259198)
        val singaporeMarkerState = rememberUpdatedMarkerState(position = centerPos)
        val cameraPositionState = rememberCameraPositionState {
            CameraPosition(centerPos, 16f, 0f, 30f)
            position = CameraPosition(centerPos, 16.5f, 40f, 10f)
        }
        val geofencePoints = remember {
            listOf(
                LatLng(
                    49.0905196956559,
                    8.398798890587775
                ),
                LatLng(
                    49.08709794084221,
                    8.397082276818216
                ),
                LatLng(
                    49.086219628993504,
                    8.402210660454827
                ),
                LatLng(
                    49.08924798269771,
                    8.40284366178234
                )
            )
        }

        val mapRadius = 0.008 // Roughly 200m
        val worldBounds = remember {
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
        }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                scrollGesturesEnabled = false,
                zoomGesturesEnabled = false,
                tiltGesturesEnabled = false,
                rotationGesturesEnabled = false
            )
        ) {
            Marker(
                state = singaporeMarkerState,
                title = "Singapore",
                snippet = "Marker in Singapore"
            )

            Polygon(
                points = worldBounds,
                holes = listOf(geofencePoints), // This "punches" a hole in the red overlay
                fillColor = Color.Red.copy(alpha = 0.4f), // Semi-transparent red
                strokeColor = Color.Red,
                strokeWidth = 2f
            )
        }
    }
}
