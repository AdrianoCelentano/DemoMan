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
import com.adriano.demoman.ui.theme.DemoManTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DemoManTheme {
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
