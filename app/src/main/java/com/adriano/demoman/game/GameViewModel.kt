package com.adriano.demoman.game

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adriano.demoman.game.data.CreateGameRequestDto
import com.adriano.demoman.game.data.EndGameRequestDto
import com.adriano.demoman.game.data.GameApiService
import com.adriano.demoman.game.data.JoinGameRequestDto
import com.adriano.demoman.game.data.toGameSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GameViewModel @Inject constructor(
    private val gameApiService: GameApiService
) : ViewModel() {

    val gameState = MutableStateFlow(GameViewState())

    init {
        gameState
            .onEach { Log.d("qwer", "State: $it") }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: GameEvent) {
        Log.d("qwer", "Event: $event")
        when (event) {
            GameEvent.CreateGame -> createGame()
            is GameEvent.JoinGame -> joinGame(event)
            GameEvent.GoToGameList -> goToGameList()
            GameEvent.EndGame -> endGame()
            GameEvent.GoToSetup -> gameState.update { it.copy(step = GameStep.Setup) }
        }
    }

    private fun endGame() {
        viewModelScope.launch {
            gameState.update { it.copy(step = GameStep.Loading) }
            gameApiService.endGame(EndGameRequestDto(gameState.value.game.id!!))
            gameState.update { it.copy(step = GameStep.Setup) }
        }
    }

    private fun goToGameList() {
        viewModelScope.launch {
            gameState.update { it.copy(step = GameStep.Loading) }
            val games = gameApiService.loadGames().body()?.map { it.toGameSession() }
                ?: emptyList()
            gameState.update { it.copy(step = GameList(games)) }
        }
    }

    private fun joinGame(event: GameEvent.JoinGame) {
        viewModelScope.launch {
            gameState.update { it.copy(step = GameStep.Loading) }
            val game = gameApiService.joinGame(JoinGameRequestDto(gameId = event.gameId)).body()
                ?.toGameSession()
                ?: throw IllegalStateException("Game must not be null")
            gameState.update { it.copy(step = GameStep.Game, game = game) }
        }
    }

    private fun createGame() {
        viewModelScope.launch {
            gameState.update { it.copy(step = GameStep.Loading) }
            val game = gameApiService.createGame(CreateGameRequestDto()).body()?.toGameSession()
                ?: throw IllegalStateException("game must not be null")
            gameState.update { it.copy(step = GameStep.Game, game = game) }
        }
    }
}
