package com.adriano.demoman.game.domain.handler

import android.annotation.SuppressLint
import android.location.Location
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.adriano.demoman.game.data.ActivateTowerRequestDto
import com.adriano.demoman.game.data.GameApiService
import com.adriano.demoman.game.data.LocationProvider
import com.adriano.demoman.game.data.UpdatePlayerPositionRequest
import com.adriano.demoman.game.data.toGameSession
import com.adriano.demoman.game.domain.GameEvent
import com.adriano.demoman.game.domain.GameSessionState
import com.adriano.demoman.game.domain.NavigationState
import com.adriano.demoman.game.domain.Team
import com.adriano.demoman.game.domain.VibrationService
import com.adriano.demoman.game.domain.calculateDebugState
import com.adriano.demoman.game.domain.isWithinRange
import com.adriano.demoman.game.domain.simulateWalkingRoute
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@SuppressLint("MissingPermission")
class GameSessionHandler(
    private val locationProvider: LocationProvider,
    private val gameApiService: GameApiService,
    private val vibrationService: VibrationService,
    private val coroutineScope: CoroutineScope,
    private val gameSessionState: MutableStateFlow<GameSessionState>,
    private val playerPositionFlow: MutableStateFlow<LatLng?>,
    private val navigationState: MutableStateFlow<NavigationState>,
    private val timer: MutableStateFlow<Long?>,
    private val sessionStateHandle: GameSessionStateHandle,
    private val savedStateHandle: SavedStateHandle
) {

    private val DEBUG_ENABLED = false
    private var timerJob: Job? = null
    private val activatingTowers = mutableSetOf<Int>()

    init {
        coroutineScope.launch {
            launch {
                while (isActive) {
                    runCatching { handleEvent(GameEvent.UpdateMisterXPosition) }
                    delay(5.minutes)
                }
            }
        }
    }

    fun handleEvent(event: GameEvent) {
        Log.d("GameSessionHandler", "Event: $event")
        when (event) {
            GameEvent.ObserveLocation -> observePlayerLocation()
            is GameEvent.ActivateTower -> activateTower(event)
            is GameEvent.PlayerPositionUpdate -> playerPositionUpdate(event)
            GameEvent.StartGameTimer -> startGameTimer()
            is GameEvent.ObserveGameState -> observeGameUpdates(event.coroutineScope)
            GameEvent.UpdateMisterXPosition -> updateMisterXPosition()
            GameEvent.UpdateGame -> coroutineScope.launch { fetchGameState() }
            GameEvent.EndGame -> endGame()
        }
    }

    private fun startGameTimer() {
        if (timerJob != null) return
        timerJob = coroutineScope.launch {
            var seconds = timer.value ?: sessionStateHandle.calculateRemainingTime(
                gameSessionState.value.game.startTimeStamp,
                gameSessionState.value.game.gameDurationInMinutes
            )
            while (seconds >= 0 && isActive) {
                timer.value = seconds
                savedStateHandle[GameSessionStateHandle.KEY_REMAINING_TIME] = seconds
                delay(1000)
                seconds--
            }
            if (seconds < 0) {
                handleEvent(GameEvent.EndGame)
            }
        }
    }

    suspend fun lastLocation(): Location {
        return locationProvider.lastLocation()
    }

    private fun activateTower(event: GameEvent.ActivateTower) {
        if (activatingTowers.contains(event.towerIndex)) return
        activatingTowers.add(event.towerIndex)
        val game = gameSessionState.value.game
        val request = ActivateTowerRequestDto(game.id!!, event.towerIndex)
        coroutineScope.launch {
            try {
                val response = gameApiService.activateTower(request)
                if (response.isSuccessful) {
                    val updatdedGame = response.body()!!.toGameSession()
                    vibrationService.triggerVibration()
                    gameSessionState.update { it.copy(game = updatdedGame) }
                } else {
                    activatingTowers.remove(event.towerIndex)
                }
            } catch (e: Exception) {
                Log.e("GameSessionHandler", "Failed to activate tower", e)
                activatingTowers.remove(event.towerIndex)
            }
        }
    }

    private fun observePlayerLocation() {
        if (DEBUG_ENABLED && gameSessionState.value.game.role == Team.MISTER_X) {
            coroutineScope.launch {
                val lastPosition = lastLocation()
                val towers = listOf(
                    LatLng(
                        lastPosition.latitude,
                        lastPosition.longitude
                    )
                ) + gameSessionState.value.game.towers.map { it.position }
                simulateWalkingRoute(towers, speedKmh = 15f)
                    .collect { handleEvent(GameEvent.PlayerPositionUpdate(it)) }
            }
        } else {
            locationProvider.locationsFlow()
                .onEach { handleEvent(GameEvent.PlayerPositionUpdate(it)) }
                .launchIn(coroutineScope)
        }
    }

    private fun playerPositionUpdate(event: GameEvent.PlayerPositionUpdate) {
        val playerPosition = event.position
        val game = gameSessionState.value.game
        playerPositionFlow.value = playerPosition
        if (game.role == Team.DETECTIVE) return
        game.towers.forEachIndexed { index, tower ->
            if (tower.position.isWithinRange(playerPosition) && tower.isActive.not() && !activatingTowers.contains(
                    index
                )
            ) {
                handleEvent(GameEvent.ActivateTower(index))
            }
        }

        if (DEBUG_ENABLED) {
            gameSessionState.update {
                it.copy(
                    debugState = calculateDebugState(
                        playerPosition,
                        game.towers
                    )
                )
            }
        }
    }

    private fun observeGameUpdates(viewLifecycleScope: CoroutineScope) {
        if (gameSessionState.value.game.role == Team.MISTER_X) return
        viewLifecycleScope.launch {
            launch {
                while (isActive) {
                    runCatching { handleEvent(GameEvent.UpdateGame) }
                    delay(13.seconds)
                }
            }
        }
    }

    fun updateMisterXPosition() {
        val game = gameSessionState.value.game
        if (game.role == Team.DETECTIVE) return
        val demoMan = game.players.first { it.team == Team.MISTER_X }
        coroutineScope.launch {
            val latLng = playerPositionFlow.value ?: return@launch
            gameApiService.updatePlayerPosition(
                UpdatePlayerPositionRequest(
                    game.id!!,
                    demoMan.userId,
                    latLng
                )
            )
        }
    }

    private suspend fun fetchGameState() {
        val gameDto =
            gameApiService.findGameById(gameSessionState.value.game.id!!).body()!!
        val game = gameDto.toGameSession().copy(
            role = Team.DETECTIVE,
        )
        if (gameSessionState.value.game.towers.count { it.isActive } != game.towers.count { it.isActive }) {
            vibrationService.triggerVibration()
        }
        gameSessionState.update { it.copy(game = game) }
    }

    private fun endGame() {
        coroutineScope.launch {
            navigationState.update { NavigationState.Loading }
            gameApiService.endGame(gameSessionState.value.game.id!!)
            timerJob?.cancel()
            timerJob = null
            sessionStateHandle.clearPersistedSession()
            activatingTowers.clear()
            navigationState.update { NavigationState.Setup }
            gameSessionState.update { GameSessionState() }
            timer.value = null
        }
    }
}
