package com.adriano.demoman.game.domain

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adriano.demoman.game.data.ActivateTowerRequestDto
import com.adriano.demoman.game.data.GameApiService
import com.adriano.demoman.game.data.GameSessionRepository
import com.adriano.demoman.game.data.LocationProvider
import com.adriano.demoman.game.data.UpdatePlayerPositionRequest
import com.adriano.demoman.game.data.toGameSession
import com.adriano.demoman.game.domain.handler.CreateGameHandler
import com.adriano.demoman.game.domain.handler.GameListHandler
import com.adriano.demoman.game.domain.handler.GameSessionHandler
import com.adriano.demoman.game.domain.handler.GameSessionStateHandle
import com.adriano.demoman.game.domain.handler.MenuHandler
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@SuppressLint("MissingPermission")
@HiltViewModel
class GameViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameApiService: GameApiService,
    private val locationProvider: LocationProvider,
    private val gameSessionRepository: GameSessionRepository,
    private val vibrationService: VibrationService,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val playerPositionFlow: MutableStateFlow<LatLng?> = MutableStateFlow(null)
    val timer: MutableStateFlow<Long?> = MutableStateFlow(null)
    val navigationState = MutableStateFlow<NavigationState>(NavigationState.Setup)
    val gameSessionState = MutableStateFlow(GameSessionState())
    val createGameState = MutableStateFlow(CreateGameStep())

    fun onMenuEvent(event: MenuEvent) {
        menuHandler.handleEvent(event)
    }

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
        navigationState = navigationState,
        timer = timer
    )

    private val gameSessionHandler = GameSessionHandler(
        locationProvider = locationProvider,
        gameApiService = gameApiService,
        vibrationService = vibrationService,
        coroutineScope = viewModelScope,
        gameSessionState = gameSessionState,
        playerPositionFlow = playerPositionFlow,
        navigationState = navigationState,
        timer = timer,
        sessionStateHandle = sessionStateHandle,
        savedStateHandle = savedStateHandle
    )

    init {
        sessionStateHandle.restoreSessionIfNeeded()
    }

    private val createGameHandler = CreateGameHandler(
        createGameState = createGameState,
        navigationState = navigationState,
        coroutineScope = viewModelScope,
        gameApiService = gameApiService,
        onGameCreated = { game, startTimeStamp ->
            val remainingTime =
                sessionStateHandle.calculateRemainingTime(startTimeStamp, game.gameDurationInMinutes)
            sessionStateHandle.persistSession(game.id!!, game.role, startTimeStamp)
            navigationState.update { NavigationState.Game }
            gameSessionState.update { it.copy(game = game) }
            timer.value = remainingTime
            vibrationService.triggerVibration()
        }
    )

    private val menuHandler = MenuHandler(
        navigationState = navigationState,
        coroutineScope = viewModelScope,
        gameApiService = gameApiService
    )

    private val gameListHandler = GameListHandler(
        navigationState = navigationState,
        gameSessionState = gameSessionState,
        timer = timer,
        coroutineScope = viewModelScope,
        gameApiService = gameApiService,
        sessionHandler = sessionStateHandle,
        onTriggerVibration = { vibrationService.triggerVibration() }
    )
}
