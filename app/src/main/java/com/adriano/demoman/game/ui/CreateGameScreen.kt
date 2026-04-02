package com.adriano.demoman.game.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.adriano.demoman.R
import com.adriano.demoman.game.domain.CreateGameStep
import com.adriano.demoman.game.domain.CreateGameSteps
import com.adriano.demoman.game.domain.GameEvent
import com.adriano.demoman.game.domain.GameViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

@SuppressLint("MissingPermission")
@Composable
fun CreateGameScreen(innerPadding: PaddingValues, viewModel: GameViewModel = hiltViewModel()) {

    val state = viewModel.gameState.collectAsState().value.step
    if (state !is CreateGameStep) return

    BackHandler { viewModel.onEvent(GameEvent.CreateGameBack) }

    var permissionRequestCount by remember { mutableIntStateOf(0) }
    val hasLocationPermission = hasLocationPermission(permissionRequestCount)

    if (hasLocationPermission) {
        val scale by animateFloatAsState(
            targetValue = if (state.step != CreateGameSteps.Boundary) 1f else 0f,
            animationSpec = tween(
                durationMillis = 2000,
                delayMillis = 500,
                easing = FastOutSlowInEasing
            ),
            label = "MarkerScale"
        )

        val context = LocalContext.current
        val mapStyleOptions = remember {
            MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style)
        }
        val playground = remember(state.bounds) { createOuterBounds(state.bounds) }
        val cameraPositionState = rememberCameraPositionState()
        LaunchedEffect(hasLocationPermission) {
            if (hasLocationPermission) {
                val location = viewModel.lastLocation()
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(
                            location.latitude,
                            location.longitude
                        ), 15f
                    ), 1000
                )
            }
        }
        LaunchedEffect(state.bounds.size) {
            if (state.bounds.size == 4) {
                val boundsBuilder = LatLngBounds.Builder()
                state.bounds.forEach { boundsBuilder.include(it) }
                cameraPositionState.animate(
                    update = newLatLngBounds(boundsBuilder.build(), 20),
                    durationMs = 1000
                )
            }
        }

        CreateGameMap(
            innerPadding,
            cameraPositionState,
            hasLocationPermission,
            state,
            mapStyleOptions,
            viewModel,
            context,
            playground,
            scale
        )
    } else {
        LocationPermissionScreen(
            onRequestPermission = { permissionRequestCount++ }
        )
    }
}

@Composable
private fun CreateGameMap(
    innerPadding: PaddingValues,
    cameraPositionState: CameraPositionState,
    hasLocationPermission: Boolean,
    state: CreateGameStep,
    mapStyleOptions: MapStyleOptions,
    viewModel: GameViewModel,
    context: Context,
    playground: List<LatLng>,
    scale: Float
) {
    Box(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
    ) {

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapStyleOptions = if (state.step != CreateGameSteps.Boundary) mapStyleOptions else null
            ),
            uiSettings = MapUiSettings(),
            onMapClick = { position ->
                viewModel.onEvent(GameEvent.CreateGameMapClick(position))
            }
        ) {
            state.bounds.forEachIndexed { index, position ->
                Marker(
                    rememberUpdatedMarkerState(position),
                    onClick = { marker ->
                        viewModel.onEvent(GameEvent.CreateGameMapClick(marker.position))
                        true
                    }
                )
            }
            val towerBitmap = remember { getResizedBitmap(context, R.drawable.tower, 104, 104) }
            state.towers.forEach { position ->
                Marker(
                    state = rememberUpdatedMarkerState(position),
                    icon = towerBitmap,
                    onClick = { marker ->
                        viewModel.onEvent(GameEvent.CreateGameMapClick(marker.position))
                        true
                    }
                )
            }

            if (state.step != CreateGameSteps.Boundary) {
                Polygon(
                    points = playground,
                    holes = listOf(state.bounds),
                    fillColor = MaterialTheme.colorScheme.background.copy(alpha = scale * 0.75f),
                    strokeColor = MaterialTheme.colorScheme.background.copy(alpha = scale),
                    strokeWidth = 4f
                )
            }
        }

        if (state.step == CreateGameSteps.Complete) {
            MainActionButton(
                text = "START !",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(56.dp),
                isPrimary = true,
                onClick = { viewModel.onEvent(GameEvent.CreateGame) }
            )
        }

        var showDialog by remember { mutableStateOf(false) }

        IconButton(
            onClick = { showDialog = true },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(56.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Mission Details",
                modifier = Modifier.size(28.dp)
            )
        }

        if (showDialog) {
            CreateGameDialog(
                initialName = state.missionName,
                initialPassword = state.password,
                onDismiss = { showDialog = false },
                onSave = { name, pass ->
                    viewModel.onEvent(GameEvent.UpdateCreateGameDetails(name, pass))
                    showDialog = false
                }
            )
        }

    }
}

@Composable
fun CreateGameDialog(
    initialName: String,
    initialPassword: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var password by remember { mutableStateOf(initialPassword) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "MISSION DETAILS",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Geben Sie Ihrer Mission einen Namen und ein optionales Passwort.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Missionsname") },
                    placeholder = { Text("Z.B. Operation Midnight") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Passwort (Optional)") },
                    placeholder = { Text("••••••••") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, password) },
                enabled = name.isNotBlank()
            ) {
                Text(
                    "SPEICHERN",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (name.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "ABBRECHEN",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                )
            }
        }
    )
}