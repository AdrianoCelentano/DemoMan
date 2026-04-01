package com.adriano.demoman.game.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import com.adriano.demoman.R
import com.adriano.demoman.game.domain.GameEvent
import com.adriano.demoman.game.domain.GameSession
import com.adriano.demoman.game.domain.Team
import com.adriano.demoman.game.domain.Tower
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

@Composable
fun GameMapScreen(
    innerPadding: PaddingValues,
    onEvent: (GameEvent) -> Unit,
    game: GameSession
) {

    val hasLocationPermission = hasLocationPermission()
    if (hasLocationPermission) onEvent(GameEvent.ObserveLocation)
    ExitGameDialog(onEvent)

    Box(
        modifier = Modifier.padding(innerPadding)
    ) {
        val context = LocalContext.current
        val centerPos = remember { findCenter(game.playground) }

        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition(centerPos, 16.5f, 0f, 10f)
        }

        val mapStyleOptions = remember {
            MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style)
        }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapStyleOptions = mapStyleOptions
            ),
            uiSettings = MapUiSettings(
                scrollGesturesEnabled = false,
                zoomGesturesEnabled = false,
                tiltGesturesEnabled = false,
                rotationGesturesEnabled = false,
                myLocationButtonEnabled = false
            )
        ) {
            if (game.role == Team.MISTER_X) MisterXView(game.towers)
            if (game.role == Team.DETECTIVE) DetectiveView(game.towers)

            val bounds = remember { createOuterBounds(game.playground) }
            Polygon(
                points = bounds,
                holes = listOf(game.playground),
                fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                strokeColor = MaterialTheme.colorScheme.primary,
                strokeWidth = 4f
            )
        }
    }
}

@Composable
fun DetectiveView(towers: List<Tower>) {
    val context = LocalContext.current
    val smallIcon = remember {
        getResizedBitmap(context, R.drawable.tower_down, 48, 48)
    }
    towers.filter { it.isActive }.forEach { tower ->
        Marker(
            icon = smallIcon,
            state = rememberUpdatedMarkerState(position = tower.position),
        )
    }

}

fun getResizedBitmap(context: Context, resId: Int, widthDp: Int, heightDp: Int): BitmapDescriptor {
    val resources = context.resources
    val widthPx = (widthDp * resources.displayMetrics.density).toInt()
    val heightPx = (heightDp * resources.displayMetrics.density).toInt()

    val bitmap = BitmapFactory.decodeResource(resources, resId)
    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, widthPx, heightPx, false)

    return BitmapDescriptorFactory.fromBitmap(scaledBitmap)
}

@Composable
fun MisterXView(towers: List<Tower>) {
    val context = LocalContext.current
    val towerIcon = remember {
        getResizedBitmap(context, R.drawable.tower, 104, 104)
    }
    val towerDownIcon = remember {
        getResizedBitmap(context, R.drawable.tower_down, 104, 104)
    }
    towers.forEach { tower ->
        Marker(
            icon = if (tower.isActive) towerDownIcon else towerIcon,
            state = rememberUpdatedMarkerState(position = tower.position),
        )
    }
}

// TODO move this to the backend
fun createOuterBounds(playground: List<LatLng>): List<LatLng> {
    if (playground.isEmpty()) return emptyList()

    val minLat = playground.minOf { it.latitude }
    val maxLat = playground.maxOf { it.latitude }
    val minLng = playground.minOf { it.longitude }
    val maxLng = playground.maxOf { it.longitude }

    val offset = 0.0045

    return listOf(
        LatLng(maxLat + offset, minLng - offset), // Top Left
        LatLng(maxLat + offset, maxLng + offset), // Top Right
        LatLng(minLat - offset, maxLng + offset), // Bottom Right
        LatLng(minLat - offset, minLng - offset)  // Bottom Left
    )
}

fun findCenter(points: List<LatLng>): LatLng {
    val builder = LatLngBounds.Builder()
    for (point in points) {
        builder.include(point)
    }
    return builder.build().center
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
