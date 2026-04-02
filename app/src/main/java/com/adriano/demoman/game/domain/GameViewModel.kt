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
import androidx.core.content.getSystemService
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adriano.demoman.game.data.ActivateTowerRequestDto
import com.adriano.demoman.game.data.CreateGameRequestDto
import com.adriano.demoman.game.data.GameApiService
import com.adriano.demoman.game.data.GameSessionRepository
import com.adriano.demoman.game.data.JoinGameRequestDto
import com.adriano.demoman.game.data.LatLngDto
import com.adriano.demoman.game.data.LocationProvider
import com.adriano.demoman.game.data.toGameSession
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class GameViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameApiService: GameApiService,
    private val locationProvider: LocationProvider,
    private val gameSessionRepository: GameSessionRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val DEBUG_ENABLED = false

    private var gameUpdatesJob: Job? = null
    private var timerJob: Job? = null
    val gameState = MutableStateFlow(GameViewState())
    
    private val activatingTowers = mutableSetOf<Int>()

    init {
        gameState
            .onEach { Log.d("VM", "State: $it") }
            .launchIn(viewModelScope)

        restoreSessionIfNeeded()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun onEvent(event: GameEvent) {
        Log.d("VM", "Event: $event")
        when (event) {
            GameEvent.CreateGame -> createGame()
            is GameEvent.JoinGame -> joinGame(event)
            GameEvent.GoToGameList -> goToGameList()
            GameEvent.EndGame -> endGame()
            GameEvent.GoToSetup -> gameState.update { it.copy(step = GameStep.Setup) }
            GameEvent.ObserveLocation -> observeLocation()
            is GameEvent.ActivateTower -> activateTower(event)
            GameEvent.GoToCreateGame -> gameState.update { it.copy(step = CreateGameStep()) }
            is GameEvent.CreateGameMapClick -> createGameMapClick(event.position)
            is GameEvent.UpdateCreateGameDetails -> updateCreateGameDetails(event)
            GameEvent.CreateGameBack -> createGameBack()
            is GameEvent.PlayerPositionUpdate -> playerPositionUpdate(event)
            GameEvent.StartGameTimer -> startGameTimer()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGameTimer() {
        if (timerJob != null) return
        timerJob = viewModelScope.launch {
            var seconds = gameState.value.remainingTime ?: calculateRemainingTime(
                gameState.value.game.startTimeStamp,
                gameState.value.game.gameDurationInMinutes
            )
            while (seconds >= 0 && isActive) {
                gameState.update { it.copy(remainingTime = seconds) }
                savedStateHandle[KEY_REMAINING_TIME] = seconds
                delay(1000)
                seconds--
            }
            if (seconds < 0) {
                onEvent(GameEvent.EndGame)
            }
        }
    }

    private fun calculateRemainingTime(startTimeStamp: Long?, durationInMinutes: Long): Long {
        if (startTimeStamp == null) return durationInMinutes * 60
        val elapsedMillis = System.currentTimeMillis() - startTimeStamp
        val elapsedSeconds = elapsedMillis / 1000
        return (durationInMinutes * 60 - elapsedSeconds).coerceAtLeast(0)
    }

    private fun createGameBack() {
        val createGameState = gameState.value.step
        if (createGameState !is CreateGameStep) return
        when {
            createGameState.bounds.isEmpty() -> gameState.update { it.copy(step = GameStep.Setup) }
            createGameState.towers.isEmpty() -> removeBoundary(createGameState.bounds.last())
            else -> removeTower(createGameState.towers.last())
        }
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
                //Create Game and navigate to game Map
            }
        }
    }

    private fun addTower(position: LatLng) {
        val createGameState = gameState.value.step
        if (createGameState !is CreateGameStep) return
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
        val newState = createGameState.copy(bounds = newBounds)
        gameState.update { it.copy(step = newState) }
    }

    private fun removeBoundary(position: LatLng) {
        val createGameState = gameState.value.step
        if (createGameState !is CreateGameStep) return
        val newBounds = createGameState.bounds - position
        val newState = createGameState.copy(bounds = newBounds)
        gameState.update { it.copy(step = newState) }
    }


    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun lastLocation(): Location {
        return locationProvider.lastLocation()
    }

    private fun activateTower(event: GameEvent.ActivateTower) {
        if (activatingTowers.contains(event.towerIndex)) return
        activatingTowers.add(event.towerIndex)

        Log.d("qwer", "activate tower ${event.towerIndex}")
        val game = gameState.value.game
        val request = ActivateTowerRequestDto(game.id!!, event.towerIndex)
        viewModelScope.launch {
            try {
                val response = gameApiService.activateTower(request)
                if (response.isSuccessful) {
                    val updatdedGame = response.body()!!.toGameSession()
                    triggerVibration()
                    Log.d("qwer", "new game towers ${updatdedGame.towers}")
                    gameState.update { it.copy(game = updatdedGame) }
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
    private fun observeLocation() {
        when (gameState.value.game.role) {
            Team.MISTER_X -> observePlayerLocation()
            Team.DETECTIVE -> observeGameUpdates()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun observePlayerLocation() {
        flow {
            gameState.value.game.towers.forEachIndexed { index, tower ->
                delay(5.seconds)
                Log.d("qwer", "indx: $index, position: ${tower.position}")
                emit(onEvent(GameEvent.PlayerPositionUpdate(tower.position)))
            }
        }.launchIn(viewModelScope)
//        locationProvider.locationsFlow()
//            .onEach { onEvent(GameEvent.PlayerPositionUpdate(it)) }
//            .launchIn(viewModelScope)
    }

    @SuppressLint("MissingPermission")
    private fun playerPositionUpdate(event: GameEvent.PlayerPositionUpdate) {
        Log.d("qwer", "new player loaction")
        val playerPosition = event.position
        val game = gameState.value.game
        game.towers.forEachIndexed { index, tower ->
            if (tower.position.isWithinRange(playerPosition) && tower.isActive.not() && !activatingTowers.contains(index)) {
                Log.d("qwer", "tower in Range")
                onEvent(GameEvent.ActivateTower(index))
            }
        }

        if (DEBUG_ENABLED && game.role == Team.MISTER_X) {
            gameState.update {
                it.copy(
                    debugState = calculateDebugState(
                        playerPosition,
                        game.towers
                    )
                )
            }
        }
    }

    private fun observeGameUpdates() {
        if (gameState.value.game.role == Team.DETECTIVE) {
            gameUpdatesJob = viewModelScope.launch {
                while (isActive) {
                    val gameDto = gameApiService.findGameById(gameState.value.game.id!!).body()!!
                    val game = gameDto.toGameSession().copy(role = Team.DETECTIVE)
                    if (gameState.value.game.towers.count { it.isActive } != game.towers.count { it.isActive })
                        gameState.update { it.copy(game = game) }
                    delay(1.minutes)
                }
            }
        }
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
            gameState.update { it.copy(step = GameStep.Loading) }
            gameApiService.endGame(gameState.value.game.id!!)
            gameUpdatesJob?.cancel()
            gameUpdatesJob = null
            timerJob?.cancel()
            timerJob = null
            clearPersistedSession()
            activatingTowers.clear()
            gameState.update {
                it.copy(
                    step = GameStep.Setup,
                    remainingTime = null,
                    debugState = null
                )
            }
        }
    }

    private fun goToGameList() {
        viewModelScope.launch {
            gameState.update { it.copy(step = GameStep.Loading) }
            val games = gameApiService.loadGames().body()?.map { it.toGameSession() }
                ?: emptyList()
            gameState.update { it.copy(step = GameList(games)) }
        }
    }

    private fun joinGame(event: GameEvent.JoinGame) {
        viewModelScope.launch {
            gameState.update { it.copy(step = GameStep.Loading) }
            val response = gameApiService.joinGame(
                JoinGameRequestDto(
                    gameId = event.gameId,
                    password = event.password
                )
            )
            if (response.isSuccessful) {
                val gameDto =
                    response.body() ?: throw IllegalStateException("Game must not be null")
                val game = gameDto.toGameSession()
                val remainingTime =
                    calculateRemainingTime(gameDto.startTimeStamp, game.gameDurationInMinutes)
                persistSession(game.id!!, game.role, gameDto.startTimeStamp)
                gameState.update {
                    it.copy(
                        step = GameStep.Game,
                        game = game,
                        remainingTime = remainingTime
                    )
                }
            } else {
                // Handle error (e.g., wrong password)
                // For now, just go back to the list
                goToGameList()
            }
        }
    }

    private fun createGame() {
        val createGameState = gameState.value.step
        if (createGameState !is CreateGameStep) return
        viewModelScope.launch {
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
            val remainingTime = calculateRemainingTime(startTimeStamp, game.gameDurationInMinutes)
            persistSession(game.id!!, game.role, startTimeStamp)
            gameState.update {
                it.copy(
                    step = GameStep.Game,
                    game = game,
                    remainingTime = remainingTime
                )
            }
            triggerVibration()
        }
    }

    private suspend fun persistSession(gameId: String, team: Team, startTimeStamp: Long?) {
        // SavedStateHandle — survives process death
        savedStateHandle[KEY_GAME_ID] = gameId
        savedStateHandle[KEY_TEAM] = team.name
        savedStateHandle[KEY_START_TIMESTAMP] = startTimeStamp
        // DataStore — survives full app kill / device reboot
        gameSessionRepository.save(gameId, team.name, startTimeStamp)
    }

    private suspend fun clearPersistedSession() {
        savedStateHandle.remove<String>(KEY_GAME_ID)
        savedStateHandle.remove<String>(KEY_TEAM)
        savedStateHandle.remove<Long>(KEY_START_TIMESTAMP)
        gameSessionRepository.clear()
    }

    /**
     * On startup, check if a game session was previously saved (survives process death
     * via [SavedStateHandle], and full app restarts via [GameSessionRepository]).
     *
     * Priority: SavedStateHandle (faster, in-memory) → DataStore (disk, survives restarts)
     */
    private fun restoreSessionIfNeeded() {
        // SavedStateHandle survives process death without a network round-trip.
        val savedGameId: String? = savedStateHandle[KEY_GAME_ID]
        val savedTeam: String? = savedStateHandle[KEY_TEAM]
        val savedStartTime: Long? = savedStateHandle[KEY_START_TIMESTAMP]

        if (savedGameId != null && savedTeam != null) {
            val team = runCatching { Team.valueOf(savedTeam) }.getOrNull() ?: return
            restoreSession(savedGameId, team, savedStartTime)
            return
        }

        // DataStore survives full restarts (e.g. phone reboot, manual swipe-close).
        viewModelScope.launch {
            val persisted = gameSessionRepository.load() ?: return@launch
            val team = runCatching { Team.valueOf(persisted.team) }.getOrNull() ?: return@launch
            restoreSession(persisted.gameId, team, persisted.startTimeStamp)
        }
    }

    private fun restoreSession(gameId: String, team: Team, startTimeStamp: Long?) {
        viewModelScope.launch {
            gameState.update { it.copy(step = GameStep.Loading) }
            try {
                val gameDto = gameApiService.findGameById(gameId).body()
                val game = gameDto?.toGameSession()?.copy(role = team)
                if (game != null) {
                    val actualStartTime = gameDto.startTimeStamp ?: startTimeStamp
                    val remainingTime =
                        calculateRemainingTime(actualStartTime, game.gameDurationInMinutes)
                    gameState.update {
                        it.copy(
                            step = GameStep.Game,
                            game = game,
                            remainingTime = remainingTime
                        )
                    }
                    Log.d(
                        "qwer",
                        "Session restored for gameId=$gameId team=$team remainingTime=$remainingTime"
                    )
                } else {
                    // Game no longer exists on the server — clean up and go to setup.
                    clearPersistedSession()
                    gameState.update { it.copy(step = GameStep.Setup) }
                }
            } catch (e: Exception) {
                Log.e("qwer", "Failed to restore session", e)
                gameState.update { it.copy(step = GameStep.Setup) }
            }
        }
    }

    companion object {
        private const val KEY_GAME_ID = "game_id"
        private const val KEY_TEAM = "team"
        private const val KEY_START_TIMESTAMP = "start_timestamp"
        private const val KEY_REMAINING_TIME = "remaining_time"
    }
}
