package com.adriano.demoman.game.domain.handler

import com.adriano.demoman.game.data.GameApiService
import com.adriano.demoman.game.data.JoinGameRequestDto
import com.adriano.demoman.game.data.toGameSession
import com.adriano.demoman.game.domain.GameListEvent
import com.adriano.demoman.game.domain.GameSession
import com.adriano.demoman.game.domain.GameSessionState
import com.adriano.demoman.game.domain.NavigationState
import com.adriano.demoman.game.domain.time.calculateRemainingTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameListHandler(
    private val navigationService: NavigationService,
    private val gameSessionState: MutableStateFlow<GameSessionState>,
    private val gameListState: MutableStateFlow<List<GameSession>>,
    private val timer: MutableStateFlow<Long?>,
    private val coroutineScope: CoroutineScope,
    private val gameApiService: GameApiService,
    private val sessionHandler: GameSessionStateHandle,
    private val onTriggerVibration: () -> Unit
) {

    fun handleEvent(event: GameListEvent) {
        when (event) {
            GameListEvent.GoToGameList -> goToGameList()
            is GameListEvent.JoinGame -> joinGame(event)
        }
    }

    private fun goToGameList() {
        coroutineScope.launch {
            navigationService.navigateTo(NavigationState.Loading)
            val games = gameApiService.loadGames().body()?.map { it.toGameSession() }
                ?: emptyList()
            gameListState.value = games
            navigationService.navigateTo(NavigationState.GameList)
        }
    }

    private fun joinGame(event: GameListEvent.JoinGame) {
        coroutineScope.launch {
            navigationService.navigateTo(NavigationState.Loading)
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
                    calculateRemainingTime(
                        gameDto.startTimeStamp,
                        game.gameDurationInMinutes
                    )
                sessionHandler.persistSession(game.id!!, game.role, gameDto.startTimeStamp)
                navigationService.navigateTo(NavigationState.Game)
                gameSessionState.update { it.copy(game = game) }
                timer.value = remainingTime
                onTriggerVibration()
            } else {
                // Handle error (e.g., wrong password)
                // For now, just go back to the list
                handleEvent(GameListEvent.GoToGameList)
            }
        }
    }
}
