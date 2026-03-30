package com.adriano.demoman.game.data

import com.adriano.demoman.game.domain.GameSession
import com.google.android.gms.maps.model.LatLng
import com.adriano.demoman.game.domain.Player
import com.adriano.demoman.game.domain.Team.DETECTIVE
import com.adriano.demoman.game.domain.Team.MISTER_X
import com.adriano.demoman.game.domain.Tower

fun GameDto.toGameSession(): GameSession {
    return GameSession(
        id = id,
        players = players.map { it.toPlayer() },
        towers = towers.map { it.toTowers() },
        playground = playgroundBoundaries.map { LatLng(it.latitude, it.longitude) }
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