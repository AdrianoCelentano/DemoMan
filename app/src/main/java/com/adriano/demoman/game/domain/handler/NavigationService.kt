package com.adriano.demoman.game.domain.handler

import androidx.compose.runtime.staticCompositionLocalOf

import com.adriano.demoman.game.domain.NavigationState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class NavigationEvent {
    data class NavigateTo(val state: NavigationState) : NavigationEvent()
    data object NavigateBack : NavigationEvent()
}

@Singleton
class NavigationService @Inject constructor() {
    private val _navigationEvents = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()

    fun navigateTo(state: NavigationState) {
        _navigationEvents.trySend(NavigationEvent.NavigateTo(state))
    }

    fun navigateBack() {
        _navigationEvents.trySend(NavigationEvent.NavigateBack)
    }
}

val LocalNavigationService = staticCompositionLocalOf<NavigationService> {
    error("No NavigationService provided")
}
