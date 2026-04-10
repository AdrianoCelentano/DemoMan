package com.adriano.demoman.game.domain

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.annotation.RequiresPermission
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adriano.demoman.game.data.GameApiService
import com.adriano.demoman.game.data.GameSessionRepository
import com.adriano.demoman.game.data.LocationProvider
import com.adriano.demoman.game.domain.handler.CreateGameHandler
import com.adriano.demoman.game.domain.handler.GameListHandler
import com.adriano.demoman.game.domain.handler.GameSessionHandler
import com.adriano.demoman.game.domain.handler.GameSessionStateHandle
import com.adriano.demoman.game.domain.handler.NavigationService
import com.adriano.demoman.game.domain.time.calculateRemainingTime
import com.adriano.demoman.game.domain.vibration.VibrationService
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@SuppressLint("MissingPermission")
@HiltViewModel
class GameViewModel @Inject constructor(
    private val gameApiService: GameApiService,
    private val locationProvider: LocationProvider,
    private val gameSessionRepository: GameSessionRepository,
    private val vibrationService: VibrationService,
    private val savedStateHandle: SavedStateHandle,
    private val navigationService: NavigationService,
) : ViewModel() {

    val playerPositionFlow: MutableStateFlow<LatLng?> = MutableStateFlow(null)
    val timer: MutableStateFlow<Long?> = MutableStateFlow(null)

    val gameSessionState = MutableStateFlow(GameSessionState())
    val createGameState = MutableStateFlow(CreateGameStep())
    val gameListState = MutableStateFlow<List<GameSession>>(emptyList())

    fun onGameListEvent(event: GameListEvent) {
        gameListHandler.handleEvent(event)
    }

    fun onCreateGameEvent(event: CreateGameEvent) {
        createGameHandler.handleEvent(event)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun onEvent(event: GameEvent) {
        gameSessionHandler.handleEvent(event)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun lastLocation(): Location {
        return locationProvider.lastLocation()
    }



    private val sessionStateHandle = GameSessionStateHandle(
        savedStateHandle = savedStateHandle,
        gameSessionRepository = gameSessionRepository,
        gameApiService = gameApiService,
        coroutineScope = viewModelScope,
        gameSessionState = gameSessionState,
        navigationService = navigationService,
        timer = timer
    )

    private val gameSessionHandler = GameSessionHandler(
        locationProvider = locationProvider,
        gameApiService = gameApiService,
        vibrationService = vibrationService,
        coroutineScope = viewModelScope,
        gameSessionState = gameSessionState,
        playerPositionFlow = playerPositionFlow,
        navigationService = navigationService,
        timer = timer,
        sessionStateHandle = sessionStateHandle,
        savedStateHandle = savedStateHandle
    )

    init {
        sessionStateHandle.restoreSessionIfNeeded()
    }

    private val createGameHandler = CreateGameHandler(
        createGameState = createGameState,
        navigationService = navigationService,
        coroutineScope = viewModelScope,
        gameApiService = gameApiService,
        onGameCreated = { game, startTimeStamp ->
            val remainingTime =
                calculateRemainingTime(startTimeStamp, game.gameDurationInMinutes)
            sessionStateHandle.persistSession(game.id!!, game.role, startTimeStamp)
            navigationService.navigateTo(NavigationState.Game)
            gameSessionState.update { it.copy(game = game) }
            timer.value = remainingTime
            vibrationService.triggerVibration()
        }
    )

    private val gameListHandler = GameListHandler(
        navigationService = navigationService,
        gameSessionState = gameSessionState,
        gameListState = gameListState,
        timer = timer,
        coroutineScope = viewModelScope,
        gameApiService = gameApiService,
        sessionHandler = sessionStateHandle,
        onTriggerVibration = { vibrationService.triggerVibration() }
    )
}
