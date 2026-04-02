package com.adriano.demoman.game.domain

import com.google.android.gms.maps.model.LatLng

data class GameViewState(
    val step: GameStep = GameStep.Setup,
    val game: GameSession = GameSession()
)

data class GameSession(
    val id: String? = null,
    val playground: List<LatLng> = emptyList(),
    val players: List<Player> = emptyList(),
    val towers: List<Tower> = emptyList(),
    val role: Team = Team.DETECTIVE
) {
    val detetctive get() = players.filter { it.team == Team.DETECTIVE }
    val misterX get() = players.filter { it.team == Team.MISTER_X }
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

sealed class GameStep {
    object Loading : GameStep()
    object Setup : GameStep()
    object Game : GameStep()
}

data class CreateGameStep(
    val bounds: List<LatLng> = emptyList(),
    val towers: List<LatLng> = emptyList()
) : GameStep() {
    val step: CreateGameSteps get() {
        return when {
            bounds.size < 4 -> CreateGameSteps.Boundary
            towers.size < 3 -> CreateGameSteps.Tower
            else -> CreateGameSteps.Complete
        }
    }
}

enum class CreateGameSteps {Boundary, Tower, Complete}

data class GameList(
    val games: List<GameSession>
) : GameStep()

sealed class GameEvent {
    object GoToGameList : GameEvent()
    object ObserveLocation : GameEvent()
    object GoToSetup : GameEvent()
    object GoToCreateGame : GameEvent()
    object CreateGame : GameEvent()
    data class CreateGameMapClick(val position: LatLng) : GameEvent()
    data class ActivateTower(val towerIndex: Int): GameEvent()
    data class JoinGame(val gameId: String) : GameEvent()
    object EndGame : GameEvent() // after all towers are activated or MisterX got caught
}