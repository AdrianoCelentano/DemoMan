package com.adriano.demoman.game.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface GameApiService {

    @POST("/api/games")
    suspend fun createGame(
        @Body request: CreateGameRequestDto
    ): Response<GameDto>

    @GET("/api/games")
    suspend fun loadGames(): Response<List<GameDto>>

    @POST("/api/game/join")
    suspend fun joinGame(
        @Body request: JoinGameRequestDto
    ): Response<GameDto>

    @POST
    suspend fun endGame(@Body request: EndGameRequestDto)
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
    val isActive: Boolean,
    val position: LatLngDto
)

data class PlayerDto(
    val userId: Long,
    val team: TeamDto,
    val position: LatLngDto
)

data class GameDto(
    val id: String? = null,
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

data class EndGameRequestDto(
    val gameId: String,
)
