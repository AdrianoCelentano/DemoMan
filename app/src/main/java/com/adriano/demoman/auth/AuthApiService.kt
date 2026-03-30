package com.adriano.demoman.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {

    @POST("auth/login")
    suspend fun login(
        @Body request: UsernamePasswordRequest
    ): Response<JwtResponse>

    @POST("auth/register")
    suspend fun register(
        @Body request: UsernamePasswordRequest
    ): Response<JwtResponse>
}

data class UsernamePasswordRequest(
    val username: String,
    val password: String
)

data class JwtResponse(
    val token: String
)