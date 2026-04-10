package com.adriano.demoman.game.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.adriano.demoman.R
import com.adriano.demoman.game.domain.CreateGameStep
import com.adriano.demoman.game.domain.CreateGameSteps
import com.adriano.demoman.game.domain.CreateGameEvent
import com.adriano.demoman.game.domain.GameViewModel
import com.adriano.demoman.game.domain.location.createOuterBounds
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

    val state = viewModel.createGameState.collectAsState().value

    BackHandler { viewModel.onCreateGameEvent(CreateGameEvent.CreateGameBack) }

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

        val playground = remember(state.bounds) { createOuterBounds(state.bounds) }
        val cameraPositionState = rememberCameraPositionState()
        LaunchedEffect(Unit) {
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
        LaunchedEffect(state.bounds.size) {
            if (state.bounds.size == 4) {
                val boundsBuilder = LatLngBounds.Builder()
                state.bounds.forEach { boundsBuilder.include(it) }
                cameraPositionState.animate(
                    update = newLatLngBounds(boundsBuilder.build(), 30),
                    durationMs = 1000
                )
            }
        }

        CreateGameMap(
            innerPadding,
            cameraPositionState,
            state,
            viewModel,
            playground,
            scale
        )
    } else {
        LocationPermissionScreen(
            onRequestPermission = { permissionRequestCount++ }
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun CreateGameMap(
    innerPadding: PaddingValues,
    cameraPositionState: CameraPositionState,
    state: CreateGameStep,
    viewModel: GameViewModel,
    playground: List<LatLng>,
    scale: Float
) {
    val context = LocalContext.current
    val isBoundaryStep = state.step == CreateGameSteps.Boundary
    val mapStyleOptions = remember(isBoundaryStep) {
        val resourceId = if (isBoundaryStep) R.raw.gray_map_style
        else R.raw.map_style
        MapStyleOptions.loadRawResourceStyle(context, resourceId)
    }

    Box(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
    ) {

        GoogleMap(
            modifier = Modifier
                .fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = true,
                mapStyleOptions = mapStyleOptions,
                minZoomPreference = 14f,
            ),
            uiSettings = MapUiSettings(
                scrollGesturesEnabled = isBoundaryStep,
                zoomGesturesEnabled = isBoundaryStep,
                tiltGesturesEnabled = isBoundaryStep,
                rotationGesturesEnabled = isBoundaryStep,
                myLocationButtonEnabled = isBoundaryStep,
                compassEnabled = isBoundaryStep,
                zoomControlsEnabled = isBoundaryStep
            ),
            onMapClick = { position ->
                viewModel.onCreateGameEvent(CreateGameEvent.CreateGameMapClick(position))
            }
        ) {
            val towerBitmap =
                remember { getResizedBitmapDescriptor(context, R.drawable.tower, 104, 104) }
            state.towers.forEach { position ->
                Marker(
                    state = rememberUpdatedMarkerState(position),
                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.8f),
                    icon = towerBitmap,
                    onClick = { marker ->
                        viewModel.onCreateGameEvent(CreateGameEvent.CreateGameMapClick(marker.position))
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

        MapIcon(context, cameraPositionState, state)

        if (state.step == CreateGameSteps.Complete) {
            MainActionButton(
                text = "START !",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(56.dp),
                isPrimary = true,
                onClick = { viewModel.onCreateGameEvent(CreateGameEvent.CreateGame) }
            )
        }

        var showDialog by remember { mutableStateOf(false) }

        AnimatedVisibility(!isBoundaryStep, modifier = Modifier.align(Alignment.TopEnd)) {
            IconButton(
                onClick = { showDialog = true },
                modifier = Modifier
                    .padding(16.dp)
                    .size(56.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Edit Mission Details",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        if (showDialog) {
            CreateGameDialog(
                initialName = state.missionName,
                initialPassword = state.password,
                initialDuration = state.gameDurationInMinutes,
                onDismiss = { showDialog = false },
                onSave = { name, pass, duration ->
                    viewModel.onCreateGameEvent(CreateGameEvent.UpdateCreateGameDetails(name, pass, duration))
                    showDialog = false
                }
            )
        }

    }
}

@Composable
private fun MapIcon(
    context: Context,
    cameraPositionState: CameraPositionState,
    state: CreateGameStep
) {
    val cornerBitmap = remember { getResizedImageBitmap(context, R.drawable.border_marker, 82, 82) }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val projection = cameraPositionState.projection ?: return@Canvas
        val imageWidth = cornerBitmap.width
        val imageHeight = cornerBitmap.height

        state.bounds.forEach { latLng ->
            val screenPosition = projection.toScreenLocation(latLng)
            drawImage(
                image = cornerBitmap,
                topLeft = Offset(
                    x = screenPosition.x.toFloat() - (imageWidth / 2f),
                    y = screenPosition.y.toFloat() - imageHeight.toFloat() + imageHeight.toFloat() / 7
                )
            )
        }
    }
}

@Composable
fun CreateGameDialog(
    initialName: String,
    initialPassword: String,
    initialDuration: Long,
    onDismiss: () -> Unit,
    onSave: (String, String, Long) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var password by remember { mutableStateOf(initialPassword) }
    var durationText by remember { mutableStateOf(initialDuration.toString()) }

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
                    text = "Geben Sie Ihrer Mission einen Namen, ein Passwort und die Dauer.",
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

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = durationText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) durationText = it },
                    label = { Text("Spieldauer (Minuten)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                onClick = { onSave(name, password, durationText.toLongOrNull() ?: 60L) },
                enabled = name.isNotBlank() && durationText.isNotBlank()
            ) {
                Text(
                    "SPEICHERN",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (name.isNotBlank() && durationText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.3f
                        )
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
