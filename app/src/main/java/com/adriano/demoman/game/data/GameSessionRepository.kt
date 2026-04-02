package com.adriano.demoman.game.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_GAME_ID = stringPreferencesKey("game_id")
private val KEY_TEAM = stringPreferencesKey("team")
private val KEY_REMAINING_TIME = longPreferencesKey("remaining_time")

data class PersistedGameSession(
    val gameId: String,
    val team: String,
    val remainingTime: Long?
)

@Singleton
class GameSessionRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    /**
     * Returns the persisted [PersistedGameSession] if one exists, or null otherwise.
     */
    suspend fun load(): PersistedGameSession? {
        return dataStore.data
            .map { prefs ->
                val id = prefs[KEY_GAME_ID]
                val team = prefs[KEY_TEAM]
                val time = prefs[KEY_REMAINING_TIME]
                if (id != null && team != null) PersistedGameSession(id, team, time) else null
            }
            .firstOrNull()
    }

    /**
     * Persists the session details so the session can be restored after process death or restart.
     */
    suspend fun save(gameId: String, team: String, remainingTime: Long?) {
        dataStore.edit { prefs ->
            prefs[KEY_GAME_ID] = gameId
            prefs[KEY_TEAM] = team
            if (remainingTime != null) {
                prefs[KEY_REMAINING_TIME] = remainingTime
            } else {
                prefs.remove(KEY_REMAINING_TIME)
            }
        }
    }

    /**
     * Updates only the remaining time in the persisted session.
     */
    suspend fun updateRemainingTime(remainingTime: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_REMAINING_TIME] = remainingTime
        }
    }

    /**
     * Clears the persisted session data (e.g., when a game ends).
     */
    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_GAME_ID)
            prefs.remove(KEY_TEAM)
            prefs.remove(KEY_REMAINING_TIME)
        }
    }
}
