package com.adriano.demoman.game.domain.handler

import com.adriano.demoman.game.data.GameApiService
import com.adriano.demoman.game.data.JoinGameRequestDto
import com.adriano.demoman.game.data.toGameSession
import com.adriano.demoman.game.domain.GameEvent
import com.adriano.demoman.game.domain.GameSessionState
import com.adriano.demoman.game.domain.NavigationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameListHandler(
    private val navigationState: MutableStateFlow<NavigationState>,
    private val gameSessionState: MutableStateFlow<GameSessionState>,
    private val timer: MutableStateFlow<Long?>,
    private val coroutineScope: CoroutineScope,
    private val gameApiService: GameApiService,
    private val sessionHandler: GameSessionHandler,
    private val onTriggerVibration: () -> Unit
) {

    fun handleEvent(event: GameEvent) {
        when (event) {
            GameEvent.GoToGameList -> goToGameList()
            is GameEvent.JoinGame -> joinGame(event)
            else -> {}
        }
    }

    private fun goToGameList() {
        coroutineScope.launch {
            navigationState.update { NavigationState.Loading }
            val games = gameApiService.loadGames().body()?.map { it.toGameSession() }
                ?: emptyList()
            navigationState.update { NavigationState.GameList(games) }
        }
    }

    private fun joinGame(event: GameEvent.JoinGame) {
        coroutineScope.launch {
            navigationState.update { NavigationState.Loading }
            val response = gameApiService.joinGame(
                JoinGameRequestDto(
                    gameId = event.gameId,
                    password = event.password
                )
            )
            if (response.isSuccessful) {
                val gameDto =
                    response.body() ?: throw IllegalStateException("Game must not be null")
                val game = gameDto.toGameSession()
                val remainingTime =
                    sessionHandler.calculateRemainingTime(
                        gameDto.startTimeStamp,
                        game.gameDurationInMinutes
                    )
                sessionHandler.persistSession(game.id!!, game.role, gameDto.startTimeStamp)
                navigationState.update { NavigationState.Game }
                gameSessionState.update { it.copy(game = game) }
                timer.value = remainingTime
                onTriggerVibration()
            } else {
                // Handle error (e.g., wrong password)
                // For now, just go back to the list
                handleEvent(GameEvent.GoToGameList)
            }
        }
    }
}
