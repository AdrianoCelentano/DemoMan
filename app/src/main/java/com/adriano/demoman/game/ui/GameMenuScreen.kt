package com.adriano.demoman.game.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.adriano.demoman.R
import com.adriano.demoman.game.domain.NavigationState
import com.adriano.demoman.game.domain.GameViewModel
import com.adriano.demoman.game.domain.GameListEvent
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adriano.demoman.game.domain.handler.NavigationEvent
import com.adriano.demoman.game.domain.handler.LocalNavigationService

@Composable
fun GameScreen(innerPadding: PaddingValues, viewModel: GameViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val timer = viewModel.timer.collectAsState().value
    val navigationService = LocalNavigationService.current

    LaunchedEffect(Unit) {
        navigationService.navigationEvents.collect { event ->
            when (event) {
                is NavigationEvent.NavigateTo -> {
                    navController.navigate(event.state)
                }

                NavigationEvent.NavigateBack -> {
                    navController.popBackStack()
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = NavigationState.Setup as NavigationState
    ) {
        composable<NavigationState.Setup> {
            SetupScreen(innerPadding = innerPadding)
        }
        composable<NavigationState.Loading> {
            LoadingScreen()
        }
        composable<NavigationState.CreateGame> {
            CreateGameScreen(innerPadding)
        }
        composable<NavigationState.GameList> {
            val games = viewModel.gameListState.collectAsState().value
            GameListScreen(
                games = games,
                onEvent = viewModel::onGameListEvent,
                onBack = { navController.popBackStack() },
                innerPadding = innerPadding
            )
        }
        composable<NavigationState.Game> {
            val gameSessionState = viewModel.gameSessionState.collectAsState().value
            GameMapScreen(
                innerPadding,
                viewModel::onEvent,
                gameSessionState.game,
                timer,
                gameSessionState.debugState
            )
        }
    }
}

@Composable
fun SetupScreen(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(innerPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Illustration
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(24.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.demoman_illustration),
                    contentDescription = "DemoMan Illustration",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = "DEMOMAN",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = "BEREIT FÜR DIE JAGD ?",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            val navigationService = LocalNavigationService.current

            // Buttons
            MainActionButton(
                text = "NEUES SPIEL",
                onClick = { navigationService.navigateTo(NavigationState.CreateGame) },
                isPrimary = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            MainActionButton(
                text = "SPIEL BEITRETEN",
                onClick = { navigationService.navigateTo(NavigationState.GameList) },
                isPrimary = false
            )
        }
    }
}

@Composable
fun MainActionButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    isPrimary: Boolean
) {
    val backgroundColor = if (isPrimary) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    val contentColor = if (isPrimary) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }

    val modifierInternal = if (isPrimary) {
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
    } else {
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    }

    Box(
        modifier = modifierInternal,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(
            modifier = Modifier.align(
                Alignment.Center
            )
        )
    }
}