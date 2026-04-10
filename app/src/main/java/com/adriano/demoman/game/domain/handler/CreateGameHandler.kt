package com.adriano.demoman.game.domain.handler

import com.adriano.demoman.game.data.CreateGameRequestDto
import com.adriano.demoman.game.data.GameApiService
import com.adriano.demoman.game.data.LatLngDto
import com.adriano.demoman.game.data.toGameSession
import com.adriano.demoman.game.domain.CreateGameStep
import com.adriano.demoman.game.domain.CreateGameSteps
import com.adriano.demoman.game.domain.GameEvent
import com.adriano.demoman.game.domain.GameSession
import com.adriano.demoman.game.domain.NavigationState
import com.adriano.demoman.game.domain.orderClockwise
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreateGameHandler(
    private val createGameState: MutableStateFlow<CreateGameStep>,
    private val navigationState: MutableStateFlow<NavigationState>,
    private val coroutineScope: CoroutineScope,
    private val gameApiService: GameApiService,
    private val onGameCreated: suspend (GameSession, Long) -> Unit
) {

    fun handleEvent(event: GameEvent) {
        when (event) {
            GameEvent.CreateGame -> createGame()
            is GameEvent.CreateGameMapClick -> createGameMapClick(event.position)
            is GameEvent.UpdateCreateGameDetails -> updateCreateGameDetails(event)
            GameEvent.CreateGameBack -> createGameBack()
            else -> {}
        }
    }

    private fun createGameBack() {
        val step = createGameState.value
        when {
            step.bounds.isEmpty() -> navigationState.update { NavigationState.Setup }
            step.towers.isEmpty() -> removeAllBoundaries()
            else -> removeTower(step.towers.last())
        }
    }

    private fun removeAllBoundaries() {
        createGameState.update { it.copy(bounds = emptyList()) }
    }

    private fun updateCreateGameDetails(event: GameEvent.UpdateCreateGameDetails) {
        createGameState.update { it.copy(
            missionName = event.name,
            password = event.pass,
            gameDurationInMinutes = event.duration
        )}
    }

    private fun createGameMapClick(position: LatLng) {
        val currentStep = createGameState.value
        when (currentStep.step) {
            CreateGameSteps.Boundary -> {
                if (currentStep.bounds.contains(position)) removeBoundary(position)
                else addBoundary(position)
            }

            CreateGameSteps.Tower -> {
                if (currentStep.towers.contains(position)) removeTower(position)
                else addTower(position)
            }

            CreateGameSteps.Complete -> {
                if (currentStep.towers.contains(position)) removeTower(position)
            }
        }
    }

    private fun addTower(position: LatLng) {
        val step = createGameState.value
        val builder = LatLngBounds.Builder()
        step.bounds.forEach { builder.include(it) }
        if (builder.build().contains(position).not()) return
        createGameState.update { it.copy(towers = it.towers + position) }
    }

    private fun removeTower(position: LatLng) {
        createGameState.update { it.copy(towers = it.towers - position) }
    }


    private fun addBoundary(position: LatLng) {
        createGameState.update { it.copy(bounds = (it.bounds + position).orderClockwise()) }
    }

    private fun removeBoundary(position: LatLng) {
        createGameState.update { it.copy(bounds = it.bounds - position) }
    }

    private fun createGame() {
        val currentStep = createGameState.value
        coroutineScope.launch {
            navigationState.update { NavigationState.Loading }
            val startTimeStamp = System.currentTimeMillis()
            val gameDto = gameApiService.createGame(
                CreateGameRequestDto(
                    missionName = currentStep.missionName,
                    password = currentStep.password.ifBlank { null },
                    bounds = currentStep.bounds.map { LatLngDto(it.latitude, it.longitude) },
                    towers = currentStep.towers.map { LatLngDto(it.latitude, it.longitude) },
                    startTimeStamp = startTimeStamp,
                    gameDurationInMinutes = currentStep.gameDurationInMinutes
                )
            ).body() ?: throw IllegalStateException("game must not be null")
            val game = gameDto.toGameSession()
            onGameCreated(game, startTimeStamp)
        }
    }
}
