package com.adriano.demoman.game.data

import com.adriano.demoman.game.GameSession
import com.adriano.demoman.game.LatLng
import com.adriano.demoman.game.Player
import com.adriano.demoman.game.Team.DETECTIVE
import com.adriano.demoman.game.Team.MISTER_X
import com.adriano.demoman.game.Tower

fun GameDto.toGameSession(): GameSession {
    return GameSession(
        id = id,
        players = players.map { it.toPlayer() },
        towers = towers.map { it.toTowers() }
    )
}

fun TowerDto.toTowers(): Tower {
    return Tower(
        isActive = isActive,
        position = LatLng(
            position.latitude,
            position.longitude
        )
    )
}

fun PlayerDto.toPlayer(): Player {
    return Player(
        userId = userId,
        team = if (team == TeamDto.DETECTIVE) DETECTIVE else MISTER_X,
        position = LatLng(
            position.latitude,
            position.longitude
        )
    )
}