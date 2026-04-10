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
import com.adriano.demoman.game.domain.MenuEvent
import com.adriano.demoman.game.domain.NavigationState
import com.adriano.demoman.game.domain.GameViewModel
import com.adriano.demoman.game.domain.GameListEvent

@Composable
fun GameScreen(innerPadding: PaddingValues, viewModel: GameViewModel = hiltViewModel()) {
    val navState = viewModel.navigationState.collectAsState().value
    val timer = viewModel.timer.collectAsState().value
    when (navState) {
        is NavigationState.GameList -> GameListScreen(navState.games, viewModel::onGameListEvent, { viewModel.onMenuEvent(MenuEvent.GoToSetup) }, innerPadding)
        NavigationState.Game -> {
            val gameSessionState = viewModel.gameSessionState.collectAsState().value
            GameMapScreen(innerPadding, viewModel::onEvent, gameSessionState.game, timer, gameSessionState.debugState)
        }
        NavigationState.Loading -> LoadingScreen()
        NavigationState.Setup -> SetupScreen(
            onGoToCreateGame = { viewModel.onMenuEvent(MenuEvent.GoToCreateGame) },
            onGoToGameList = { viewModel.onGameListEvent(GameListEvent.GoToGameList) },
            innerPadding = innerPadding
        )
        NavigationState.CreateGame -> CreateGameScreen(innerPadding)
    }
}

@Composable
fun SetupScreen(onGoToCreateGame: () -> Unit, onGoToGameList: () -> Unit, innerPadding: PaddingValues) {
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

            // Buttons
            MainActionButton(
                text = "NEUES SPIEL",
                onClick = onGoToCreateGame,
                isPrimary = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            MainActionButton(
                text = "SPIEL BEITRETEN",
                onClick = onGoToGameList,
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