package com.adriano.demoman.game.data

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.util.UUID

class FakeGameApiService : GameApiService {

    private val games = mutableListOf(
        GameDto(
            id = "demo-game-1",
            players = listOf(
                PlayerDto(100L, TeamDto.DETECTIVE, LatLngDto(52.5200, 13.4050))
            ),
            playgroundBoundaries = eggensteinMap,
            towers = generateRandomTowers()
        )
    )

    override suspend fun createGame(request: CreateGameRequestDto): Response<GameDto> {
        val newGame = GameDto(
            id = UUID.randomUUID().toString(),
            playgroundBoundaries = eggensteinMap,
            players = listOf(
                PlayerDto(
                    userId = System.currentTimeMillis(),
                    team = request.team,
                    position = LatLngDto(52.5200, 13.4050)
                )
            ),
            towers = generateRandomTowers()
        )
        games.add(newGame)
        return Response.success(newGame)
    }

    override suspend fun loadGames(): Response<List<GameDto>> {
        return Response.success(games.toList())
    }

    override suspend fun findGameById(id: String): Response<GameDto> {
        val index = games.indexOfFirst { it.id == id }
        if (index == -1) {
            val errorBody = "Game not found".toResponseBody("text/plain".toMediaTypeOrNull())
            return Response.error(404, errorBody)
        }
        val game = games[index]
        return Response.success(game)
    }

    override suspend fun joinGame(request: JoinGameRequestDto): Response<GameDto> {
        val index = games.indexOfFirst { it.id == request.gameId }
        if (index == -1) {
            val errorBody = "Game not found".toResponseBody("text/plain".toMediaTypeOrNull())
            return Response.error(404, errorBody)
        }

        val game = games[index]
        val updatedPlayers = game.players + PlayerDto(
            userId = System.currentTimeMillis(),
            team = request.team,
            position = LatLngDto(52.5205, 13.4055)
        )
        val updatedGame = game.copy(players = updatedPlayers)
        games[index] = updatedGame

        return Response.success(updatedGame)
    }

    override suspend fun activateTower(request: ActivateTowerRequestDto): Response<GameDto> {
        val index = games.indexOfFirst { it.id == request.gameId }
        if (index == -1) {
            val errorBody = "Game not found".toResponseBody("text/plain".toMediaTypeOrNull())
            return Response.error(404, errorBody)
        }

        val game = games[index]
        val updatedTowers = game.towers.mapIndexed { index, tower ->
            if (index == request.towerIndex) tower.copy(isActive = true) else tower
        }
        val updatedGame = game.copy(towers = updatedTowers)
        games[index] = updatedGame

        return Response.success(updatedGame)
    }

    override suspend fun endGame(request: EndGameRequestDto) {
        games.removeIf { it.id == request.gameId }
    }

    private fun generateRandomTowers(): List<TowerDto> {
        return eggensteinTowers.shuffled().take(3)
    }
}