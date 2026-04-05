package com.adriano.demoman.game.domain

import com.adriano.demoman.game.data.GameApiService
import com.adriano.demoman.game.data.toGameSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MenuHandler(
    private val gameState: MutableStateFlow<GameViewState>,
    private val coroutineScope: CoroutineScope,
    private val gameApiService: GameApiService,
) {
    fun handleEvent(event: GameEvent) {
        when (event) {
            GameEvent.GoToSetup -> gameState.update { it.copy(step = GameStep.Setup) }
            GameEvent.GoToCreateGame -> gameState.update { it.copy(step = CreateGameStep()) }
            GameEvent.GoToGameList -> goToGameList()
            else -> {}
        }
    }

    private fun goToGameList() {
        coroutineScope.launch {
            gameState.update { it.copy(step = GameStep.Loading) }
            val games = gameApiService.loadGames().body()?.map { it.toGameSession() }
                ?: emptyList()
            gameState.update { it.copy(step = GameList(games)) }
        }
    }
}
