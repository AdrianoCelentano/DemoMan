package com.adriano.demoman.game.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_GAME_ID = stringPreferencesKey("game_id")
private val KEY_TEAM = stringPreferencesKey("team")

data class PersistedGameSession(
    val gameId: String,
    val team: String
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
                if (id != null && team != null) PersistedGameSession(id, team) else null
            }
            .firstOrNull()
    }

    /**
     * Persists the [gameId] and [team] so the session can be restored after process death or restart.
     */
    suspend fun save(gameId: String, team: String) {
        dataStore.edit { prefs ->
            prefs[KEY_GAME_ID] = gameId
            prefs[KEY_TEAM] = team
        }
    }

    /**
     * Clears the persisted session data (e.g., when a game ends).
     */
    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_GAME_ID)
            prefs.remove(KEY_TEAM)
        }
    }
}
