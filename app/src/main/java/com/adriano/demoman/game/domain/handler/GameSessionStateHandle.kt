package com.adriano.demoman.game.domain.handler

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import com.adriano.demoman.game.data.GameApiService
import com.adriano.demoman.game.data.GameSessionRepository
import com.adriano.demoman.game.data.toGameSession
import com.adriano.demoman.game.domain.GameSessionState
import com.adriano.demoman.game.domain.NavigationState
import com.adriano.demoman.game.domain.Team
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameSessionStateHandle(
    private val savedStateHandle: SavedStateHandle,
    private val gameSessionRepository: GameSessionRepository,
    private val gameApiService: GameApiService,
    private val coroutineScope: CoroutineScope,
    private val gameSessionState: MutableStateFlow<GameSessionState>,
    private val navigationState: MutableStateFlow<NavigationState>,
    private val timer: MutableStateFlow<Long?>,
) {
    suspend fun persistSession(gameId: String, team: Team, startTimeStamp: Long?) {
        // SavedStateHandle — survives process death
        savedStateHandle[KEY_GAME_ID] = gameId
        savedStateHandle[KEY_TEAM] = team.name
        savedStateHandle[KEY_START_TIMESTAMP] = startTimeStamp
        // DataStore — survives full app kill / device reboot
        gameSessionRepository.save(gameId, team.name, startTimeStamp)
    }

    suspend fun clearPersistedSession() {
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
    fun restoreSessionIfNeeded() {
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
        coroutineScope.launch {
            val persisted = gameSessionRepository.load() ?: return@launch
            val team = runCatching { Team.valueOf(persisted.team) }.getOrNull() ?: return@launch
            restoreSession(persisted.gameId, team, persisted.startTimeStamp)
        }
    }

    private fun restoreSession(gameId: String, team: Team, startTimeStamp: Long?) {
        coroutineScope.launch {
            navigationState.update { NavigationState.Loading }
            try {
                val gameDto = gameApiService.findGameById(gameId).body()
                val game = gameDto?.toGameSession()?.copy(role = team)
                if (game != null) {
                    val actualStartTime = gameDto.startTimeStamp ?: startTimeStamp
                    val remainingTime =
                        calculateRemainingTime(actualStartTime, game.gameDurationInMinutes)
                    navigationState.update { NavigationState.Game }
                    gameSessionState.update { it.copy(game = game) }
                    timer.value = remainingTime
                    Log.d(
                        "qwer",
                        "Session restored for gameId=$gameId team=$team remainingTime=$remainingTime"
                    )
                } else {
                    // Game no longer exists on the server — clean up and go to setup.
                    clearPersistedSession()
                    navigationState.update { NavigationState.Setup }
                }
            } catch (e: Exception) {
                Log.e("qwer", "Failed to restore session", e)
                navigationState.update { NavigationState.Setup }
            }
        }
    }

    fun calculateRemainingTime(startTimeStamp: Long?, durationInMinutes: Long): Long {
        if (startTimeStamp == null) return durationInMinutes * 60
        val elapsedMillis = System.currentTimeMillis() - startTimeStamp
        val elapsedSeconds = elapsedMillis / 1000
        return (durationInMinutes * 60 - elapsedSeconds).coerceAtLeast(0)
    }

    companion object {
        const val KEY_GAME_ID = "game_id"
        const val KEY_TEAM = "team"
        const val KEY_START_TIMESTAMP = "start_timestamp"
        const val KEY_REMAINING_TIME = "remaining_time"
    }
}
