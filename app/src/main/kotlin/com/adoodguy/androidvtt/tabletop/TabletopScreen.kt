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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        bottomBar = { BottomBar(state) },
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

            Button(onClick = state::addToken) {
                Text("Add token")
            }
        }

        val hasMeasurement = state.measurement != null
        val hasDrawings = state.strokes.isNotEmpty() || state.activeStroke != null

        // This row always occupies the same height so the tabletop viewport does
        // not move when clear actions appear or disappear.
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
private fun BottomBar(state: TabletopState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Zoom ${(state.pixelsPerWorldUnit / 96.0 * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = "Center ${"%.2f".format(state.cameraCenter.x)}, ${"%.2f".format(state.cameraCenter.y)}",
                style = MaterialTheme.typography.labelSmall,
            )
        }

        GridSettingsMenu(state)
    }
}

@Composable
private fun GridSettingsMenu(state: TabletopState) {
    var expanded by remember { mutableStateOf(false) }
    val currentStyle = when (state.gridKind) {
        GridKind.SQUARE -> "Square"
        GridKind.HEX -> if (state.hexOrientation == HexOrientation.POINTY_TOP) {
            "Hex pointy"
        } else {
            "Hex flat"
        }
    }

    Box {
        Button(onClick = { expanded = true }) {
            Text("Grid: $currentStyle")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Text(
                text = "Style",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
            DropdownMenuItem(
                text = { Text(menuChoice(state.gridKind == GridKind.SQUARE, "Square")) },
                onClick = {
                    state.selectSquareGrid()
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        menuChoice(
                            state.gridKind == GridKind.HEX &&
                                state.hexOrientation == HexOrientation.POINTY_TOP,
                            "Hex — pointy-top",
                        ),
                    )
                },
                onClick = {
                    state.selectHexGrid(HexOrientation.POINTY_TOP)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        menuChoice(
                            state.gridKind == GridKind.HEX &&
                                state.hexOrientation == HexOrientation.FLAT_TOP,
                            "Hex — flat-top",
                        ),
                    )
                },
                onClick = {
                    state.selectHexGrid(HexOrientation.FLAT_TOP)
                    expanded = false
                },
            )

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text(menuChoice(state.snapEnabled, "Snap to grid")) },
                onClick = {
                    state.setSnapEnabled(!state.snapEnabled)
                    expanded = false
                },
            )

            HorizontalDivider()

            Text(
                text = "Scale",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
            state.unitScalePresets.forEach { units ->
                DropdownMenuItem(
                    text = {
                        Text(
                            menuChoice(
                                state.displayedUnitsPerCell == units,
                                "${units.roundToInt()} ft / cell",
                            ),
                        )
                    },
                    onClick = {
                        state.setDisplayedUnitsPerCell(units)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun BoxScope.TokenContextMenu(state: TabletopState) {
    if (!state.tokenMenuVisible) return
    val token = state.selectedToken ?: return
    var sizeMenuExpanded by remember(token.id) { mutableStateOf(false) }
    var colorMenuExpanded by remember(token.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(12.dp)
            .widthIn(min = 220.dp, max = 300.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Token settings", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = token.name,
                onValueChange = state::renameSelectedToken,
                label = { Text("Name") },
                singleLine = true,
            )

            Box {
                AssistChip(
                    onClick = { sizeMenuExpanded = true },
                    label = { Text("Size: ${token.footprint.label}") },
                )
                DropdownMenu(
                    expanded = sizeMenuExpanded,
                    onDismissRequest = { sizeMenuExpanded = false },
                ) {
                    TokenFootprint.entries.forEach { footprint ->
                        DropdownMenuItem(
                            text = {
                                Text(menuChoice(token.footprint == footprint, footprint.label))
                            },
                            onClick = {
                                state.setSelectedTokenFootprint(footprint)
                                sizeMenuExpanded = false
                            },
                        )
                    }
                }
            }

            Box {
                AssistChip(
                    onClick = { colorMenuExpanded = true },
                    label = { Text("Color: ${token.color.label}") },
                )
                DropdownMenu(
                    expanded = colorMenuExpanded,
                    onDismissRequest = { colorMenuExpanded = false },
                ) {
                    TokenColor.entries.forEach { color ->
                        DropdownMenuItem(
                            text = { Text(menuChoice(token.color == color, color.label)) },
                            onClick = {
                                state.setSelectedTokenColor(color)
                                colorMenuExpanded = false
                            },
                        )
                    }
                }
            }

            Button(onClick = state::resetSelectedToken) {
                Text("Reset to origin")
            }
            Button(onClick = state::deleteSelectedToken) {
                Text("Delete token")
            }
            Button(onClick = state::dismissTokenMenu) {
                Text("Close")
            }
        }
    }
}

private fun menuChoice(selected: Boolean, label: String): String =
    if (selected) "✓ $label" else label
