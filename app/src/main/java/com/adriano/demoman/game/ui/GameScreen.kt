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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.adriano.demoman.game.domain.GameEvent
import com.adriano.demoman.game.domain.GameList
import com.adriano.demoman.game.domain.GameSession
import com.adriano.demoman.game.domain.GameStep
import com.adriano.demoman.game.domain.GameViewModel

@Composable
fun GameScreen(innerPadding: PaddingValues, viewModel: GameViewModel = hiltViewModel()) {
    val state = viewModel.gameState.collectAsState().value
    val onEvent = viewModel::onEvent
    when (state.step) {
        is GameList -> GameListScreen(state.step.games, onEvent, innerPadding)
        GameStep.Game -> GameMapScreen(innerPadding, onEvent, state.game)
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