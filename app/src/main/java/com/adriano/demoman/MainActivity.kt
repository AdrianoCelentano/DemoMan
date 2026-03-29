package com.adriano.demoman

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.copy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.adriano.demoman.ui.theme.DemoManTheme
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DemoManTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        val centerPos = LatLng(49.08822215735542, 8.400129266259198)
                        val singaporeMarkerState = rememberUpdatedMarkerState(position = centerPos)
                        val cameraPositionState = rememberCameraPositionState {
                            CameraPosition(centerPos, 16f, 0f, 30f)
                            position = CameraPosition(centerPos, 16.5f, 40f, 10f)
                        }
                        val geofencePoints = remember {
                            listOf(
                                LatLng(
                                    49.0905196956559,
                                    8.398798890587775
                                ),
                                LatLng(
                                    49.08709794084221,
                                    8.397082276818216
                                ),
                                LatLng(
                                    49.086219628993504,
                                    8.402210660454827
                                ),
                                LatLng(
                                    49.08924798269771,
                                    8.40284366178234
                                )
                            )
                        }

                        val mapRadius = 0.008 // Roughly 200m
                        val worldBounds = remember {
                            listOf(
                                LatLng(
                                    centerPos.latitude - mapRadius,
                                    centerPos.longitude - mapRadius
                                ),
                                LatLng(
                                    centerPos.latitude + mapRadius,
                                    centerPos.longitude - mapRadius
                                ),
                                LatLng(
                                    centerPos.latitude + mapRadius,
                                    centerPos.longitude + mapRadius
                                ),
                                LatLng(
                                    centerPos.latitude - mapRadius,
                                    centerPos.longitude + mapRadius
                                )
                            )
                        }

                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            uiSettings = MapUiSettings(
                                scrollGesturesEnabled = false,
                                zoomGesturesEnabled = false,
                                tiltGesturesEnabled = false,
                                rotationGesturesEnabled = false
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
            }
        }
    }
}