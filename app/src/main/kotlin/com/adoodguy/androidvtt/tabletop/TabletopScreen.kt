package com.adoodguy.androidvtt.tabletop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.adoodguy.androidvtt.geometry.GridKind
import com.adoodguy.androidvtt.geometry.HexOrientation
import kotlin.math.roundToInt

@Composable
fun TabletopScreen() {
    val state = remember { TabletopState() }

    Scaffold(
        topBar = { PrototypeToolbar(state) },
        bottomBar = { DebugBar(state) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onSizeChanged { state.viewportSize = it },
        ) {
            TabletopCanvas(
                state = state,
                modifier = Modifier.fillMaxSize(),
            )

            measurementText(state)?.let { measurement ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                ) {
                    Text(
                        text = measurement,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            TokenContextMenu(state)
        }
    }
}

@Composable
private fun PrototypeToolbar(state: TabletopState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabletopTool.entries.forEach { tool ->
                FilterChip(
                    selected = state.tool == tool,
                    onClick = { state.tool = tool },
                    label = { Text(tool.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }

            FilterChip(
                selected = state.gridKind == GridKind.SQUARE,
                onClick = { state.gridKind = GridKind.SQUARE },
                label = { Text("Square") },
            )
            FilterChip(
                selected = state.gridKind == GridKind.HEX,
                onClick = { state.gridKind = GridKind.HEX },
                label = { Text("Hex") },
            )

            if (state.gridKind == GridKind.HEX) {
                AssistChip(
                    onClick = {
                        state.hexOrientation = when (state.hexOrientation) {
                            HexOrientation.POINTY_TOP -> HexOrientation.FLAT_TOP
                            HexOrientation.FLAT_TOP -> HexOrientation.POINTY_TOP
                        }
                    },
                    label = {
                        Text(
                            if (state.hexOrientation == HexOrientation.POINTY_TOP) {
                                "Pointy-top"
                            } else {
                                "Flat-top"
                            },
                        )
                    },
                )
            }

            FilterChip(
                selected = state.snapEnabled,
                onClick = { state.snapEnabled = !state.snapEnabled },
                label = { Text("Snap") },
            )

            AssistChip(
                onClick = state::cycleUnitScale,
                label = { Text("${state.displayedUnitsPerCell.roundToInt()} ft / cell") },
            )

        }

        val hasMeasurement = state.measurement != null
        val hasDrawings = state.strokes.isNotEmpty() || state.activeStroke != null

        // Keep this row in the layout at all times. Only its contents change,
        // so Scaffold's top padding and the tabletop viewport remain stable.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(
                space = 8.dp,
                alignment = Alignment.End,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasMeasurement) {
                Button(onClick = state::clearMeasurement) {
                    Text("Clear measurement")
                }
            }
            if (hasDrawings) {
                Button(onClick = state::clearDrawings) {
                    Text("Clear drawings")
                }
            }
        }
    }
}

@Composable
private fun DebugBar(state: TabletopState) {
    val pointerWorld = state.cameraCenter
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Zoom ${(state.pixelsPerWorldUnit / 96.0 * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = "Center ${"%.2f".format(pointerWorld.x)}, ${"%.2f".format(pointerWorld.y)}",
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = when (state.gridKind) {
                GridKind.SQUARE -> "Square"
                GridKind.HEX -> "Hex ${state.hexOrientation.name.lowercase()}"
            },
            style = MaterialTheme.typography.labelMedium,
        )
    }
}


@Composable
private fun BoxScope.TokenContextMenu(state: TabletopState) {
    if (!state.tokenMenuVisible) return
    Card(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(12.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Prototype token", style = MaterialTheme.typography.titleSmall)
            Button(onClick = state::resetToken) { Text("Reset to origin") }
            Button(onClick = state::dismissTokenMenu) { Text("Close") }
        }
    }
}
