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