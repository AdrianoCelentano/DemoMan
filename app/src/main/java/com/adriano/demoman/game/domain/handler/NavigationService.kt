package com.adriano.demoman.game.domain.handler

import com.adriano.demoman.game.domain.NavigationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

class NavigationService @Inject constructor() {
    private val _navigationState = MutableStateFlow<NavigationState>(NavigationState.Setup)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    fun navigateTo(state: NavigationState) {
        _navigationState.update { state }
    }
}
