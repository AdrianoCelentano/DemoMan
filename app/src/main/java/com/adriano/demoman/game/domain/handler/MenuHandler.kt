package com.adriano.demoman.game.domain.handler

import com.adriano.demoman.game.data.GameApiService
import com.adriano.demoman.game.data.toGameSession
import com.adriano.demoman.game.domain.MenuEvent
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
    fun handleEvent(event: MenuEvent) {
        when (event) {
            MenuEvent.GoToSetup -> navigationState.update { NavigationState.Setup }
            MenuEvent.GoToCreateGame -> navigationState.update { NavigationState.CreateGame }
        }
    }
}
