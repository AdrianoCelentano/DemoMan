package com.adriano.demoman.game.domain

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
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
import com.adriano.demoman.game.data.toGameSession
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@HiltViewModel
class GameViewModel @Inject constructor(
    private val gameApiService: GameApiService,
    private val locationProvider: LocationProvider,
    private val gameSessionRepository: GameSessionRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val gameState = MutableStateFlow(GameViewState())

    init {
        gameState
            .onEach { Log.d("qwer", "State: $it") }
            .launchIn(viewModelScope)

        restoreSessionIfNeeded()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun onEvent(event: GameEvent) {
        Log.d("qwer", "Event: $event")
        when (event) {
            GameEvent.CreateGame -> createGame()
            is GameEvent.JoinGame -> joinGame(event)
            GameEvent.GoToGameList -> goToGameList()
            GameEvent.EndGame -> endGame()
            GameEvent.GoToSetup -> gameState.update { it.copy(step = GameStep.Setup) }
            GameEvent.ObserveLocation -> observeLocation()
            is GameEvent.ActivateTower -> activateTower(event)
            GameEvent.GoToCreateGame -> gameState.update { it.copy(step = CreateGame()) }
            is GameEvent.CreateGameMapClick -> createGameMapClick(event.position)
        }
    }

    private fun createGameMapClick(position: LatLng) {
        val createGameState = gameState.value.step
        if (createGameState !is CreateGame) return
        when(createGameState.step) {
            CreateGameStep.Boundary -> {
                if (createGameState.bounds.contains(position)) removeBoundary(position)
                else addBoundary(position)
            }
            CreateGameStep.Tower -> {
                if (createGameState.towers.contains(position)) removeTower(position)
                else addTower(position)
            }
            CreateGameStep.Complete -> {
                //Create Game and navigate to game Map
            }
        }
    }

    private fun addTower(position: LatLng) {
        val createGameState = gameState.value.step
        if (createGameState !is CreateGame) return
        val newTowers = createGameState.towers + position
        val newState = createGameState.copy(towers = newTowers)
        gameState.update { it.copy(step = newState) }
    }

    private fun removeTower(position: LatLng) {
        val createGameState = gameState.value.step
        if (createGameState !is CreateGame) return
        val newTowers = createGameState.towers - position
        val newState = createGameState.copy(towers = newTowers)
        gameState.update { it.copy(step = newState) }
    }


    private fun addBoundary(position: LatLng) {
        val createGameState = gameState.value.step
        if (createGameState !is CreateGame) return
        val newBounds = createGameState.bounds + position
        val newState = createGameState.copy(bounds = newBounds)
        gameState.update { it.copy(step = newState) }
    }

    private fun removeBoundary(position: LatLng) {
        val createGameState = gameState.value.step
        if (createGameState !is CreateGame) return
        val newBounds = createGameState.bounds - position
        val newState = createGameState.copy(bounds = newBounds)
        gameState.update { it.copy(step = newState) }
    }


    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun lastLocation(): Location {
        return locationProvider.lastLocation()
    }

    private fun activateTower(event: GameEvent.ActivateTower) {
        val game = gameState.value.game
        val request = ActivateTowerRequestDto(game.id!!, event.towerIndex)
        viewModelScope.launch {
            val updatdedGame = gameApiService.activateTower(request).body()!!.toGameSession()
            gameState.update { it.copy(game = updatdedGame) }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun observeLocation() {
        when (gameState.value.game.role) {
            Team.MISTER_X -> observeTowerUpdates()
            Team.DETECTIVE -> observeGameUpdates()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun observeTowerUpdates() {
        locationProvider.locationsFlow()
            .onEach { isTowerCloseBy(it) }
            .launchIn(viewModelScope)
    }

    private fun observeGameUpdates() {
        if (gameState.value.game.role == Team.DETECTIVE) {
            viewModelScope.launch {
                while (isActive) {
                    val game = gameApiService.findGameById(gameState.value.game.id!!).body()!!
                        .toGameSession().copy(role = Team.DETECTIVE)
                    gameState.update { it.copy(game = game) }
                    delay(1.minutes)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun isTowerCloseBy(playerPosition: LatLng) {
        val game = gameState.value.game
        game.towers.filter { it.isActive.not() }.forEachIndexed { index, tower ->
            if (tower.position.isWithinRange(playerPosition)) {
                onEvent(GameEvent.ActivateTower(index))
            }
        }
    }

    fun LatLng.isWithinRange(other: LatLng, meters: Float = 20f): Boolean {
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
            clearPersistedSession()
            gameState.update { it.copy(step = GameStep.Setup) }
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
            val game = gameApiService.joinGame(JoinGameRequestDto(gameId = event.gameId)).body()
                ?.toGameSession()
                ?: throw IllegalStateException("Game must not be null")
            persistSession(game.id!!, game.role)
            gameState.update { it.copy(step = GameStep.Game, game = game) }
        }
    }

    private fun createGame() {
//        viewModelScope.launch {
//            gameState.update { it.copy(step = GameStep.Loading) }
//            val game = gameApiService.createGame(CreateGameRequestDto()).body()?.toGameSession()
//                ?: throw IllegalStateException("game must not be null")
//            persistSession(game.id!!, game.role)
//            gameState.update { it.copy(step = GameStep.Game, game = game) }
//        }
    }

    private suspend fun persistSession(gameId: String, team: Team) {
        // SavedStateHandle — survives process death
        savedStateHandle[KEY_GAME_ID] = gameId
        savedStateHandle[KEY_TEAM] = team.name
        // DataStore — survives full app kill / device reboot
        gameSessionRepository.save(gameId, team.name)
    }

    private suspend fun clearPersistedSession() {
        savedStateHandle.remove<String>(KEY_GAME_ID)
        savedStateHandle.remove<String>(KEY_TEAM)
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

        if (savedGameId != null && savedTeam != null) {
            val team = runCatching { Team.valueOf(savedTeam) }.getOrNull() ?: return
            restoreSession(savedGameId, team)
            return
        }

        // DataStore survives full restarts (e.g. phone reboot, manual swipe-close).
        viewModelScope.launch {
            val persisted = gameSessionRepository.load() ?: return@launch
            val team = runCatching { Team.valueOf(persisted.team) }.getOrNull() ?: return@launch
            restoreSession(persisted.gameId, team)
        }
    }

    private fun restoreSession(gameId: String, team: Team) {
        viewModelScope.launch {
            gameState.update { it.copy(step = GameStep.Loading) }
            try {
                val game = gameApiService.findGameById(gameId).body()
                    ?.toGameSession()
                    ?.copy(role = team)
                if (game != null) {
                    gameState.update { it.copy(step = GameStep.Game, game = game) }
                    Log.d("qwer", "Session restored for gameId=$gameId team=$team")
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
    }
}
