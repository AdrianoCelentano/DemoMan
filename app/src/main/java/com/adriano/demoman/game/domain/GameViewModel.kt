package com.adriano.demoman.game.domain

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adriano.demoman.game.data.ActivateTowerRequestDto
import com.adriano.demoman.game.data.CreateGameRequestDto
import com.adriano.demoman.game.data.EndGameRequestDto
import com.adriano.demoman.game.data.GameApiService
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
    private val locationProvider: LocationProvider
) : ViewModel() {

    val gameState = MutableStateFlow(GameViewState())

    init {
        gameState
            .onEach { Log.d("qwer", "State: $it") }
            .launchIn(viewModelScope)
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
        }
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
        if (gameState.value.game.role == Team.MISTER_X) {
            locationProvider.locationsFlow()
                .onEach { isTowerCloseBy(it) }
                .launchIn(viewModelScope)
        } else {
            viewModelScope.launch {
                while (isActive) {
                    val game = gameApiService.findGameById(gameState.value.game.id!!).body()!!
                        .toGameSession()
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
            gameState.update { it.copy(step = GameStep.Game, game = game) }
        }
    }

    private fun createGame() {
        viewModelScope.launch {
            gameState.update { it.copy(step = GameStep.Loading) }
            val game = gameApiService.createGame(CreateGameRequestDto()).body()?.toGameSession()
                ?: throw IllegalStateException("game must not be null")
            gameState.update { it.copy(step = GameStep.Game, game = game) }
        }
    }
}
