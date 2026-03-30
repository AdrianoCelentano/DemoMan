package com.adriano.demoman.game.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.adriano.demoman.game.GameEvent
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

@Composable
fun GameMapScreen(innerPadding: PaddingValues, onEvent: (GameEvent) -> Unit) {

    val hasLocationPermission = hasLocationPermission()
    ExitGameDialog(onEvent)

    Box(
        modifier = Modifier.padding(innerPadding)
    ) {
        val singaporeMarkerState = rememberUpdatedMarkerState(position = centerPos)
        val cameraPositionState = rememberCameraPositionState {
            CameraPosition(centerPos, 16f, 0f, 30f)
            position = CameraPosition(centerPos, 16.5f, 40f, 10f)
        }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(
                scrollGesturesEnabled = false,
                zoomGesturesEnabled = false,
                tiltGesturesEnabled = false,
                rotationGesturesEnabled = false,
                myLocationButtonEnabled = hasLocationPermission
            )
        ) {
            Marker(
                state = singaporeMarkerState,
                title = "Singapore",
                snippet = "Marker in Singapore"
            )

            Polygon(
                points = worldBounds,
                holes = listOf(geofencePoints), // This "punches" a hole in the red overlay
                fillColor = Color.Red.copy(alpha = 0.4f), // Semi-transparent red
                strokeColor = Color.Red,
                strokeWidth = 2f
            )
        }
    }
}

@Composable
private fun ExitGameDialog(onEvent: (GameEvent) -> Unit) {
    var showEndGameDialog by remember { mutableStateOf(false) }

    if (showEndGameDialog) {
        AlertDialog(
            onDismissRequest = { showEndGameDialog = false },
            title = { Text(text = "Spiel beenden?") },
            text = { Text(text = "Möchtest du das Spiel wirklich beenden?") },
            confirmButton = {
                TextButton(onClick = {
                    showEndGameDialog = false
                    onEvent(GameEvent.EndGame)
                }) {
                    Text("Beenden")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndGameDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    BackHandler() { showEndGameDialog = true }
}

@Composable
fun hasLocationPermission(): Boolean {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    return hasLocationPermission
}
