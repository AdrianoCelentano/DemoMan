package com.adriano.demoman.game.domain

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope

data class GameSessionState(
    val game: GameSession = GameSession(),
    val debugState: DebugViewState? = null
)

data class GameSession(
    val id: String? = null,
    val playground: List<LatLng> = emptyList(),
    val password: String? = null,
    val players: List<Player> = emptyList(),
    val towers: List<Tower> = emptyList(),
    val role: Team = Team.DETECTIVE,
    val startTimeStamp: Long? = null,
    val gameDurationInMinutes: Long = 60,
) {
    val lastMisterXPosition get() = players.firstOrNull { it.team == Team.MISTER_X }?.position
}

enum class Team {
    DETECTIVE,
    MISTER_X
}

data class Tower(
    val isActive: Boolean = false,
    val position: LatLng
)

data class Player(
    val userId: Long,
    val team: Team,
    val position: LatLng
)

sealed class NavigationState {
    object Loading : NavigationState()
    object Setup : NavigationState()
    object Game : NavigationState()
    object CreateGame : NavigationState()
    data class GameList(val games: List<GameSession>) : NavigationState()
}

data class CreateGameStep(
    val missionName: String = "IMPOSSIBLE",
    val password: String = "",
    val gameDurationInMinutes: Long = 60,
    val bounds: List<LatLng> = emptyList(),
    val towers: List<LatLng> = emptyList()
) {
    val step: CreateGameSteps
        get() {
            return when {
                bounds.size < 4 -> CreateGameSteps.Boundary
                towers.size < 3 -> CreateGameSteps.Tower
                else -> CreateGameSteps.Complete
            }
        }
}

enum class CreateGameSteps { Boundary, Tower, Complete }


sealed class MenuEvent {
    object GoToSetup : MenuEvent()
    object GoToCreateGame : MenuEvent()
}

sealed class GameListEvent {
    object GoToGameList : GameListEvent()
    data class JoinGame(val gameId: String, val password: String? = null) : GameListEvent()
}

sealed class CreateGameEvent {
    object CreateGame : CreateGameEvent()
    data class CreateGameMapClick(val position: LatLng) : CreateGameEvent()
    data class UpdateCreateGameDetails(val name: String, val pass: String, val duration: Long) : CreateGameEvent()
    object CreateGameBack : CreateGameEvent()
}

sealed class GameEvent {
    object ObserveLocation : GameEvent()
    data class PlayerPositionUpdate(val position: LatLng) : GameEvent()
    data class ObserveGameState(val coroutineScope: CoroutineScope) : GameEvent()
    object UpdateMisterXPosition : GameEvent()
    data class ActivateTower(val towerIndex: Int) : GameEvent()
    object UpdateGame : GameEvent()
    object EndGame : GameEvent() // after all towers are activated or MisterX got caught
    object StartGameTimer : GameEvent()
}