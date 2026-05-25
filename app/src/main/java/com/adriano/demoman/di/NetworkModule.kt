package com.adriano.demoman.di

import com.adriano.demoman.auth.AuthApiService
import com.adriano.demoman.auth.TokenStore
import com.adriano.demoman.game.data.FakeGameApiService
import com.adriano.demoman.game.data.GameApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                val token = TokenStore.token
                if (!token.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }
                chain.proceed(requestBuilder.build())
            }
            .connectTimeout(0, TimeUnit.MILLISECONDS) // No timeout for establishing connection
            .readTimeout(0, TimeUnit.MILLISECONDS)    // No timeout for receiving data
            .writeTimeout(0, TimeUnit.MILLISECONDS)   // No timeout for sending data
            .build()

        return Retrofit.Builder()
//            .baseUrl("https://demo-2c7p.onrender.com/") // prod
            .baseUrl("https://unreplenished-mirta-unsmitten.ngrok-free.dev/") // dev
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService {
        return retrofit.create(AuthApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideGameApiService(retrofit: Retrofit): GameApiService {
        return retrofit.create(GameApiService::class.java)
//        return FakeGameApiService()
    }
}
