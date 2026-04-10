package com.adriano.demoman

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.adriano.demoman.auth.LoginScreen
import com.adriano.demoman.auth.TokenStore
import com.adriano.demoman.game.ui.GameScreen
import com.adriano.demoman.game.domain.handler.LocalNavigationService
import com.adriano.demoman.game.domain.handler.NavigationService
import com.adriano.demoman.ui.theme.DemoManTheme
import androidx.compose.runtime.CompositionLocalProvider
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var navigationService: NavigationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DemoManTheme {
                CompositionLocalProvider(LocalNavigationService provides navigationService) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        if (TokenStore.token.isNullOrBlank()) {
                            LoginScreen(onLoginSuccess = {}, modifier = Modifier.padding(innerPadding))
                        } else {
                            GameScreen(innerPadding)
                        }
                    }
                }
            }
        }
    }
}
