package com.adriano.demoman.game.data

import androidx.compose.ui.semantics.Role
import com.google.android.gms.maps.model.LatLng
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface GameApiService {

    @POST("/api/games")
    suspend fun createGame(
        @Body request: CreateGameRequestDto
    ): Response<GameDto>

    @GET("/api/games")
    suspend fun loadGames(): Response<List<GameDto>>

    @GET("/api/game/{id}")
    suspend fun findGameById(@Path("id") id: String): Response<GameDto>

    @POST("/api/game/join")
    suspend fun joinGame(
        @Body request: JoinGameRequestDto
    ): Response<GameDto>

    @POST("/api/game/activate-tower")
    suspend fun activateTower(@Body request: ActivateTowerRequestDto): Response<GameDto>

    @DELETE("/api/game/{id}")
    suspend fun endGame(@Path("id") id: String): Response<Unit>
}

enum class TeamDto {
    DETECTIVE,
    MISTER_X
}

data class LatLngDto(
    val latitude: Double,
    val longitude: Double
)

data class TowerDto(
    val isActive: Boolean = false,
    val position: LatLngDto
)

data class PlayerDto(
    val userId: Long,
    val team: TeamDto,
    val position: LatLngDto
)

data class GameDto(
    val id: String? = null,
    val playgroundBoundaries: List<LatLngDto>,
    val players: List<PlayerDto> = emptyList(),
    val towers: List<TowerDto> = emptyList()
)

data class CreateGameRequestDto(
    val team: TeamDto = TeamDto.DETECTIVE
)

data class JoinGameRequestDto(
    val gameId: String,
    val team: TeamDto = TeamDto.MISTER_X
)

data class ActivateTowerRequestDto(
    val gameId: String,
    val towerIndex: Int,
)
