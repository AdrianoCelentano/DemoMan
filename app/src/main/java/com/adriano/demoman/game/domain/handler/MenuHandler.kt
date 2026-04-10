package com.adriano.demoman.game.domain.handler

import com.adriano.demoman.game.data.GameApiService
import com.adriano.demoman.game.data.toGameSession
import com.adriano.demoman.game.domain.GameEvent
import com.adriano.demoman.game.domain.NavigationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MenuHandler(
    private val navigationState: MutableStateFlow<NavigationState>,
    private val coroutineScope: CoroutineScope,
    private val gameApiService: GameApiService,
) {
    fun handleEvent(event: GameEvent) {
        when (event) {
            GameEvent.GoToSetup -> navigationState.update { NavigationState.Setup }
            GameEvent.GoToCreateGame -> navigationState.update { NavigationState.CreateGame }
            GameEvent.GoToGameList -> goToGameList()
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
}
