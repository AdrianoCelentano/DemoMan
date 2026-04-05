package com.adriano.demoman.game.domain

import com.adriano.demoman.game.data.CreateGameRequestDto
import com.adriano.demoman.game.data.GameApiService
import com.adriano.demoman.game.data.LatLngDto
import com.adriano.demoman.game.data.toGameSession
import com.adriano.demoman.game.ui.orderClockwise
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreateGameHandler(
    private val gameState: MutableStateFlow<GameViewState>,
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
        val createGameState = gameState.value.step
        if (createGameState !is CreateGameStep) return
        when {
            createGameState.bounds.isEmpty() -> gameState.update { it.copy(step = GameStep.Setup) }
            createGameState.towers.isEmpty() -> removeAllBoundaries()
            else -> removeTower(createGameState.towers.last())
        }
    }

    private fun removeAllBoundaries() {
        val createGameState = gameState.value.step
        if (createGameState !is CreateGameStep) return
        val newState = createGameState.copy(bounds = emptyList())
        gameState.update { it.copy(step = newState) }
    }

    private fun updateCreateGameDetails(event: GameEvent.UpdateCreateGameDetails) {
        val createGameState = gameState.value.step
        if (createGameState !is CreateGameStep) return
        val newState = createGameState.copy(
            missionName = event.name,
            password = event.pass,
            gameDurationInMinutes = event.duration
        )
        gameState.update { it.copy(step = newState) }
    }

    private fun createGameMapClick(position: LatLng) {
        val createGameState = gameState.value.step
        if (createGameState !is CreateGameStep) return
        when (createGameState.step) {
            CreateGameSteps.Boundary -> {
                if (createGameState.bounds.contains(position)) removeBoundary(position)
                else addBoundary(position)
            }

            CreateGameSteps.Tower -> {
                if (createGameState.towers.contains(position)) removeTower(position)
                else addTower(position)
            }

            CreateGameSteps.Complete -> {
                if (createGameState.towers.contains(position)) removeTower(position)
            }
        }
    }

    private fun addTower(position: LatLng) {
        val createGameState = gameState.value.step
        if (createGameState !is CreateGameStep) return
        val builder = LatLngBounds.Builder()
        createGameState.bounds.forEach { builder.include(it) }
        if (builder.build().contains(position).not()) return
        val newTowers = createGameState.towers + position
        val newState = createGameState.copy(towers = newTowers)
        gameState.update { it.copy(step = newState) }
    }

    private fun removeTower(position: LatLng) {
        val createGameState = gameState.value.step
        if (createGameState !is CreateGameStep) return
        val newTowers = createGameState.towers - position
        val newState = createGameState.copy(towers = newTowers)
        gameState.update { it.copy(step = newState) }
    }


    private fun addBoundary(position: LatLng) {
        val createGameState = gameState.value.step
        if (createGameState !is CreateGameStep) return
        val newBounds = createGameState.bounds + position
        val newState = createGameState.copy(bounds = newBounds.orderClockwise())
        gameState.update { it.copy(step = newState) }
    }

    private fun removeBoundary(position: LatLng) {
        val createGameState = gameState.value.step
        if (createGameState !is CreateGameStep) return
        val newBounds = createGameState.bounds - position
        val newState = createGameState.copy(bounds = newBounds)
        gameState.update { it.copy(step = newState) }
    }

    private fun createGame() {
        val createGameState = gameState.value.step
        if (createGameState !is CreateGameStep) return
        coroutineScope.launch {
            gameState.update { it.copy(step = GameStep.Loading) }
            val startTimeStamp = System.currentTimeMillis()
            val gameDto = gameApiService.createGame(
                CreateGameRequestDto(
                    missionName = createGameState.missionName,
                    password = createGameState.password.ifBlank { null },
                    bounds = createGameState.bounds.map { LatLngDto(it.latitude, it.longitude) },
                    towers = createGameState.towers.map { LatLngDto(it.latitude, it.longitude) },
                    startTimeStamp = startTimeStamp,
                    gameDurationInMinutes = createGameState.gameDurationInMinutes
                )
            ).body() ?: throw IllegalStateException("game must not be null")
            val game = gameDto.toGameSession()
            onGameCreated(game, startTimeStamp)
        }
    }
}
