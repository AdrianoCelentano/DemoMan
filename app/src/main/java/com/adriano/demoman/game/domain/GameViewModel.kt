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
import com.adriano.demoman.game.data.JoinGameRequestDto
import com.adriano.demoman.game.data.LocationProvider
import com.adriano.demoman.game.data.UpdatePlayerPositionRequest
import com.adriano.demoman.game.data.toGameSession
import com.adriano.demoman.game.domain.handler.CreateGameHandler
import com.adriano.demoman.game.domain.handler.GameListHandler
import com.adriano.demoman.game.domain.handler.GameSessionHandler
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
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val playerPositionFlow: MutableStateFlow<LatLng?> = MutableStateFlow(null)
    val timer: MutableStateFlow<Long?> = MutableStateFlow(null)
    private val DEBUG_ENABLED = false
    private var timerJob: Job? = null
    val navigationState = MutableStateFlow<NavigationState>(NavigationState.Setup)
    val gameSessionState = MutableStateFlow(GameSessionState())
    val createGameState = MutableStateFlow(CreateGameStep())

    private val activatingTowers = mutableSetOf<Int>()

    init {
        viewModelScope.launch {
            launch {
                while (isActive) {
                    runCatching { onEvent(GameEvent.UpdateMisterXPosition) }
                    delay(5.minutes)
                }
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun onEvent(event: GameEvent) {
        Log.d("VM", "Event: $event")
        when (event) {
            // Menu
            GameEvent.GoToSetup,
            GameEvent.GoToCreateGame -> menuHandler.handleEvent(event)
            // Game List
            GameEvent.GoToGameList,
            is GameEvent.JoinGame -> gameListHandler.handleEvent(event)
            // Game
            GameEvent.ObserveLocation -> observePlayerLocation()
            is GameEvent.ActivateTower -> activateTower(event)
            is GameEvent.PlayerPositionUpdate -> playerPositionUpdate(event)
            GameEvent.StartGameTimer -> startGameTimer()
            is GameEvent.ObserveGameState -> observeGameUpdates(event.coroutineScope)
            GameEvent.UpdateMisterXPosition -> updateMisterXPosition()
            GameEvent.UpdateGame -> viewModelScope.launch { fetchGameState() }
            GameEvent.EndGame -> endGame()
            // Create Game
            GameEvent.CreateGame,
            is GameEvent.CreateGameMapClick,
            is GameEvent.UpdateCreateGameDetails,
            GameEvent.CreateGameBack -> createGameHandler.handleEvent(event)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGameTimer() {
        if (timerJob != null) return
        timerJob = viewModelScope.launch {
            var seconds = timer.value ?: sessionHandler.calculateRemainingTime(
                gameSessionState.value.game.startTimeStamp,
                gameSessionState.value.game.gameDurationInMinutes
            )
            while (seconds >= 0 && isActive) {
                timer.value = seconds
                savedStateHandle[GameSessionHandler.KEY_REMAINING_TIME] = seconds
                delay(1000)
                seconds--
            }
            if (seconds < 0) {
                onEvent(GameEvent.EndGame)
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun lastLocation(): Location {
        return locationProvider.lastLocation()
    }

    private fun activateTower(event: GameEvent.ActivateTower) {
        if (activatingTowers.contains(event.towerIndex)) return
        activatingTowers.add(event.towerIndex)

        Log.d("qwer", "activate tower ${event.towerIndex}")
        val game = gameSessionState.value.game
        val request = ActivateTowerRequestDto(game.id!!, event.towerIndex)
        viewModelScope.launch {
            try {
                val response = gameApiService.activateTower(request)
                if (response.isSuccessful) {
                    val updatdedGame = response.body()!!.toGameSession()
                    triggerVibration()
                    Log.d("qwer", "new game towers ${updatdedGame.towers}")
                    gameSessionState.update { it.copy(game = updatdedGame) }
                } else {
                    activatingTowers.remove(event.towerIndex)
                }
            } catch (e: Exception) {
                Log.e("VM", "Failed to activate tower", e)
                activatingTowers.remove(event.towerIndex)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun triggerVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Vibrate for 500 milliseconds at default amplitude
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun observePlayerLocation() {
        if (DEBUG_ENABLED && gameSessionState.value.game.role == Team.MISTER_X) {
            viewModelScope.launch {
                val lastPosition = lastLocation()
                val towers = listOf(
                    LatLng(
                        lastPosition.latitude,
                        lastPosition.longitude
                    )
                ) + gameSessionState.value.game.towers.map { it.position }
                simulateWalkingRoute(towers, speedKmh = 15f)
                    .collect { onEvent(GameEvent.PlayerPositionUpdate(it)) }
            }
        } else {
            locationProvider.locationsFlow()
                .onEach { onEvent(GameEvent.PlayerPositionUpdate(it)) }
                .launchIn(viewModelScope)
        }
    }

    @SuppressLint("MissingPermission")
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
                onEvent(GameEvent.ActivateTower(index))
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

    @SuppressLint("MissingPermission")
    private fun observeGameUpdates(viewLifecycleScope: CoroutineScope) {
        if (gameSessionState.value.game.role == Team.MISTER_X) return
        viewLifecycleScope.launch {
            launch {
                while (isActive) {
                    runCatching { onEvent(GameEvent.UpdateGame) }
                    delay(13.seconds)
                }
            }
        }
    }

    fun updateMisterXPosition() {
        val game = gameSessionState.value.game
        if (game.role == Team.DETECTIVE) return
        val demoMan = game.players.first { it.team == Team.MISTER_X }
        viewModelScope.launch {
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
            triggerVibration()
        }
        gameSessionState.update { it.copy(game = game) }
    }

    fun LatLng.isWithinRange(other: LatLng, meters: Float = 50f): Boolean {
        val results = FloatArray(1)
        Location.distanceBetween(
            this.latitude,
            this.longitude,
            other.latitude,
            other.longitude,
            results
        )
        return results[0] < meters
    }

    private fun endGame() {
        viewModelScope.launch {
            navigationState.update { NavigationState.Loading }
            gameApiService.endGame(gameSessionState.value.game.id!!)
            timerJob?.cancel()
            timerJob = null
            sessionHandler.clearPersistedSession()
            activatingTowers.clear()
            navigationState.update { NavigationState.Setup }
            gameSessionState.update { GameSessionState() }
            timer.value = null
        }
    }



    private val sessionHandler = GameSessionHandler(
        savedStateHandle = savedStateHandle,
        gameSessionRepository = gameSessionRepository,
        gameApiService = gameApiService,
        coroutineScope = viewModelScope,
        gameSessionState = gameSessionState,
        navigationState = navigationState,
        timer = timer
    )

    init {
        sessionHandler.restoreSessionIfNeeded()
    }

    private val createGameHandler = CreateGameHandler(
        createGameState = createGameState,
        navigationState = navigationState,
        coroutineScope = viewModelScope,
        gameApiService = gameApiService,
        onGameCreated = { game, startTimeStamp ->
            val remainingTime =
                sessionHandler.calculateRemainingTime(startTimeStamp, game.gameDurationInMinutes)
            sessionHandler.persistSession(game.id!!, game.role, startTimeStamp)
            navigationState.update { NavigationState.Game }
            gameSessionState.update { it.copy(game = game) }
            timer.value = remainingTime
            triggerVibration()
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
        sessionHandler = sessionHandler,
        onTriggerVibration = { triggerVibration() }
    )
}
