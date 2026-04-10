package com.adriano.demoman.game.domain.time

fun calculateRemainingTime(startTimeStamp: Long?, durationInMinutes: Long): Long {
    if (startTimeStamp == null) return durationInMinutes * 60
    val elapsedMillis = System.currentTimeMillis() - startTimeStamp
    val elapsedSeconds = elapsedMillis / 1000
    return (durationInMinutes * 60 - elapsedSeconds).coerceAtLeast(0)
}
