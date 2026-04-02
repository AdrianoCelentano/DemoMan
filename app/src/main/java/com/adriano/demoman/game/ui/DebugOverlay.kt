package com.adriano.demoman.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adriano.demoman.game.domain.DebugViewState

@Composable
fun DebugOverlay(debugState: DebugViewState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(8.dp)
                .widthIn(max = 250.dp)
        ) {
            Text(
                "DEBUG INFO (MISTER X)",
                color = Color.Yellow,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Pos: ${debugState.playerPosition?.latitude ?: "N/A"}, ${debugState.playerPosition?.longitude ?: "N/A"}",
                color = Color.White,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "TOWER DISTANCES:",
                color = Color.Cyan,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
            LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                items(debugState.towerDistances) { item ->
                    Text(
                        "Tower: ${"%.4f".format(item.towerPosition.latitude)}, ${"%.4f".format(item.towerPosition.longitude)}",
                        color = Color.White,
                        fontSize = 9.sp
                    )
                    Text(
                        "Dist: ${"%.1f".format(item.distanceMeters)}m",
                        color = if (item.distanceMeters < 20) Color.Red else Color.Green,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}
