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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlin.math.abs
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

            WorkspaceInteractionLayer(state)
            TabletopNotesLayer(state)

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

            MeasurementMarkerMenu(state)
            TokenContextMenu(state)
        }
    }
}

@Composable
private fun PrototypeToolbar(state: TabletopState) {
    var clearMenuExpanded by remember { mutableStateOf(false) }
    val hasMeasurement = state.measurement != null
    val hasDrawings = state.strokes.isNotEmpty() || state.activeStroke != null
    val mapConfiguration = TabletopMapStore.configuration

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabletopMode.entries.forEach { mode ->
                FilterChip(
                    selected = WorkspaceModeStore.mode == mode,
                    onClick = {
                        clearMenuExpanded = false
                        WorkspaceModeStore.select(mode, state)
                    },
                    label = {
                        Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                    },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (WorkspaceModeStore.mode) {
                TabletopMode.TOKENS -> {
                    Button(onClick = state::addToken) {
                        Text("Add token")
                    }
                }

                TabletopMode.MAPS -> {
                    Button(onClick = TabletopMapStore::requestImagePicker) {
                        Text(if (mapConfiguration.hasImage) "Replace map" else "Add map")
                    }
                    if (mapConfiguration.hasImage) {
                        Button(onClick = TabletopMapStore::openSettings) {
                            Text("Map settings")
                        }
                    }
                }

                TabletopMode.TOOLS -> {
                    TabletopTool.entries.forEach { tool ->
                        FilterChip(
                            selected = state.tool == tool,
                            onClick = {
                                state.tool = tool
                                state.dismissMeasurementMarkerMenu()
                            },
                            label = {
                                Text(tool.name.lowercase().replaceFirstChar { it.uppercase() })
                            },
                        )
                    }

                    if (state.tool == TabletopTool.DRAW) {
                        DrawingColorMenu(state)
                        FilterChip(
                            selected = state.drawingMode == DrawingMode.ERASER,
                            onClick = state::toggleDrawingEraser,
                            label = { Text("Eraser") },
                        )
                    }

                    if (hasMeasurement || hasDrawings) {
                        Box {
                            Button(onClick = { clearMenuExpanded = true }) {
                                Text("Clear")
                            }
                            DropdownMenu(
                                expanded = clearMenuExpanded,
                                onDismissRequest = { clearMenuExpanded = false },
                            ) {
                                if (hasMeasurement) {
                                    DropdownMenuItem(
                                        text = { Text("Clear measurement") },
                                        onClick = {
                                            state.clearMeasurement()
                                            clearMenuExpanded = false
                                        },
                                    )
                                }
                                if (hasDrawings) {
                                    DropdownMenuItem(
                                        text = { Text("Clear drawings") },
                                        onClick = {
                                            state.clearDrawings()
                                            clearMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawingColorMenu(state: TabletopState) {
    var expanded by remember { mutableStateOf(false) }
    var customColorText by remember(state.brushColorArgb) {
        mutableStateOf(formatRgbHex(state.brushColorArgb))
    }
    var colorError by remember { mutableStateOf<String?>(null) }

    Box {
        Button(onClick = { expanded = true }) {
            Text("Ink ${formatRgbHex(state.brushColorArgb)}")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Black") },
                onClick = {
                    state.applyDrawingCustomColor("#000000")
                    expanded = false
                },
            )
            TokenColorPreset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        state.selectDrawingColorPreset(preset)
                        expanded = false
                    },
                )
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .widthIn(min = 230.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = customColorText,
                    onValueChange = {
                        customColorText = it
                        colorError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Custom #RRGGBB") },
                    singleLine = true,
                    isError = colorError != null,
                )
                colorError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Button(
                    onClick = {
                        if (state.applyDrawingCustomColor(customColorText)) {
                            colorError = null
                            expanded = false
                        } else {
                            colorError = "Use six hexadecimal digits, such as #34A8D8."
                        }
                    },
                ) {
                    Text("Apply color")
                }
            }
        }
    }
}

@Composable
private fun BoxScope.MeasurementMarkerMenu(state: TabletopState) {
    val index = state.selectedMeasurementMarkerIndex ?: return
    val path = state.measurement ?: return
    if (index !in path.points.indices) return

    Card(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Marker ${index + 1}", style = MaterialTheme.typography.labelLarge)
            Button(onClick = state::deleteMeasurementFromSelectedMarker) {
                Text("Delete this + later")
            }
            Button(onClick = state::dismissMeasurementMarkerMenu) {
                Text("Cancel")
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
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
    var customScaleText by remember(state.displayedUnitsPerCell) {
        mutableStateOf(formatDecimal(state.displayedUnitsPerCell))
    }
    var scaleError by remember { mutableStateOf<String?>(null) }

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
                    state.snapEnabled = !state.snapEnabled
                    expanded = false
                },
            )

            HorizontalDivider()

            Text(
                text = "Scale presets",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
            state.unitScalePresets.forEach { units ->
                DropdownMenuItem(
                    text = {
                        Text(
                            menuChoice(
                                nearlyEqual(state.displayedUnitsPerCell, units),
                                "${formatDecimal(units)} ft / cell",
                            ),
                        )
                    },
                    onClick = {
                        state.selectUnitScale(units)
                        expanded = false
                    },
                )
            }

            HorizontalDivider()

            Column(
                modifier = Modifier
                    .widthIn(min = 230.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Custom scale", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = customScaleText,
                    onValueChange = {
                        customScaleText = it
                        scaleError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Feet per cell") },
                    singleLine = true,
                    isError = scaleError != null,
                )
                scaleError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Button(
                    onClick = {
                        val value = customScaleText.toDoubleOrNull()
                        if (value != null && state.selectUnitScale(value)) {
                            scaleError = null
                            expanded = false
                        } else {
                            scaleError = "Enter a positive number."
                        }
                    },
                ) {
                    Text("Apply custom scale")
                }
            }
        }
    }
}

@Composable
private fun BoxScope.TokenContextMenu(state: TabletopState) {
    if (!state.tokenMenuVisible) return
    val token = state.selectedToken ?: return

    val selectedSizePreset = TokenSizePreset.entries.firstOrNull {
        nearlyEqual(token.widthCells, it.widthCells) &&
            nearlyEqual(token.heightCells, it.heightCells)
    }
    val selectedColorPreset = TokenColorPreset.entries.firstOrNull {
        token.colorArgb == it.argb
    }

    var sizeMenuExpanded by remember(token.id) { mutableStateOf(false) }
    var colorMenuExpanded by remember(token.id) { mutableStateOf(false) }
    var rotationMenuExpanded by remember(token.id) { mutableStateOf(false) }
    var markerMenuExpanded by remember(token.id) { mutableStateOf(false) }

    var customWidthText by remember(token.id, token.widthCells) {
        mutableStateOf(formatDecimal(token.widthCells))
    }
    var customHeightText by remember(token.id, token.heightCells) {
        mutableStateOf(formatDecimal(token.heightCells))
    }
    var customColorText by remember(token.id, token.colorArgb) {
        mutableStateOf(formatRgbHex(token.colorArgb))
    }
    var customRotationText by remember(token.id, token.rotationDegrees) {
        mutableStateOf(formatDecimal(token.rotationDegrees))
    }

    var sizeError by remember(token.id) { mutableStateOf<String?>(null) }
    var colorError by remember(token.id) { mutableStateOf<String?>(null) }
    var rotationError by remember(token.id) { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(12.dp)
            .widthIn(min = 240.dp, max = 320.dp),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Token settings", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = token.name,
                onValueChange = state::renameSelectedToken,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true,
            )

            Text("Direct controls", style = MaterialTheme.typography.labelMedium)
            FilterChip(
                selected = token.movementLocked,
                onClick = state::toggleSelectedTokenMovementLock,
                label = { Text("Lock movement") },
            )
            FilterChip(
                selected = token.scaleLocked,
                onClick = state::toggleSelectedTokenScaleLock,
                label = { Text("Lock scaling") },
            )
            FilterChip(
                selected = token.rotationLocked,
                onClick = state::toggleSelectedTokenRotationLock,
                label = { Text("Lock rotation") },
            )
            Text(
                "Locked direct controls are disabled and their handles are hidden. Numeric settings remain available for deliberate corrections.",
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider()

            Text("Size", style = MaterialTheme.typography.labelMedium)
            Box {
                AssistChip(
                    onClick = { sizeMenuExpanded = true },
                    label = {
                        Text(
                            selectedSizePreset?.label
                                ?: "${formatDecimal(token.widthCells)} × " +
                                "${formatDecimal(token.heightCells)} cells",
                        )
                    },
                )
                DropdownMenu(
                    expanded = sizeMenuExpanded,
                    onDismissRequest = { sizeMenuExpanded = false },
                ) {
                    TokenSizePreset.entries.forEach { preset ->
                        DropdownMenuItem(
                            text = {
                                Text(menuChoice(selectedSizePreset == preset, preset.label))
                            },
                            onClick = {
                                state.selectSelectedTokenSizePreset(preset)
                                sizeMenuExpanded = false
                                sizeError = null
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = customWidthText,
                onValueChange = {
                    customWidthText = it
                    sizeError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Custom width in cells") },
                singleLine = true,
                isError = sizeError != null,
            )
            OutlinedTextField(
                value = customHeightText,
                onValueChange = {
                    customHeightText = it
                    sizeError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Custom height in cells") },
                singleLine = true,
                isError = sizeError != null,
            )
            sizeError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Button(
                onClick = {
                    val width = customWidthText.toDoubleOrNull()
                    val height = customHeightText.toDoubleOrNull()
                    if (
                        width != null &&
                        height != null &&
                        state.applySelectedTokenCustomSize(width, height)
                    ) {
                        sizeError = null
                    } else {
                        sizeError = "Width and height must each be 0.1 to 100 cells."
                    }
                },
            ) {
                Text("Apply custom size")
            }

            HorizontalDivider()

            Text("Color", style = MaterialTheme.typography.labelMedium)
            Box {
                AssistChip(
                    onClick = { colorMenuExpanded = true },
                    label = {
                        Text(selectedColorPreset?.label ?: formatRgbHex(token.colorArgb))
                    },
                )
                DropdownMenu(
                    expanded = colorMenuExpanded,
                    onDismissRequest = { colorMenuExpanded = false },
                ) {
                    TokenColorPreset.entries.forEach { preset ->
                        DropdownMenuItem(
                            text = {
                                Text(menuChoice(selectedColorPreset == preset, preset.label))
                            },
                            onClick = {
                                state.selectSelectedTokenColorPreset(preset)
                                colorMenuExpanded = false
                                colorError = null
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = customColorText,
                onValueChange = {
                    customColorText = it
                    colorError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Custom color (#RRGGBB)") },
                singleLine = true,
                isError = colorError != null,
            )
            colorError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Button(
                onClick = {
                    if (state.applySelectedTokenCustomColor(customColorText)) {
                        colorError = null
                    } else {
                        colorError = "Use six hexadecimal digits, such as #34A8D8."
                    }
                },
            ) {
                Text("Apply custom color")
            }

            HorizontalDivider()

            Text("Rotation", style = MaterialTheme.typography.labelMedium)
            Box {
                AssistChip(
                    onClick = { rotationMenuExpanded = true },
                    label = { Text("${formatDecimal(token.rotationDegrees)}°") },
                )
                DropdownMenu(
                    expanded = rotationMenuExpanded,
                    onDismissRequest = { rotationMenuExpanded = false },
                ) {
                    rotationPresets.forEach { degrees ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    menuChoice(
                                        nearlyEqual(token.rotationDegrees, degrees),
                                        "${formatDecimal(degrees)}°",
                                    ),
                                )
                            },
                            onClick = {
                                state.selectSelectedTokenRotation(degrees)
                                rotationMenuExpanded = false
                                rotationError = null
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = customRotationText,
                onValueChange = {
                    customRotationText = it
                    rotationError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Custom rotation in degrees") },
                singleLine = true,
                isError = rotationError != null,
            )
            rotationError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Button(
                onClick = {
                    val degrees = customRotationText.toDoubleOrNull()
                    if (degrees != null && state.selectSelectedTokenRotation(degrees)) {
                        rotationError = null
                    } else {
                        rotationError = "Enter a valid number of degrees."
                    }
                },
            ) {
                Text("Apply rotation")
            }

            if (!token.isCircular) {
                Box {
                    AssistChip(
                        onClick = { markerMenuExpanded = true },
                        label = {
                            Text("Marker: ${token.orientationMarkerAxis.label}")
                        },
                    )
                    DropdownMenu(
                        expanded = markerMenuExpanded,
                        onDismissRequest = { markerMenuExpanded = false },
                    ) {
                        TokenOrientationMarkerAxis.entries.forEach { axis ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        menuChoice(
                                            token.orientationMarkerAxis == axis,
                                            axis.label,
                                        ),
                                    )
                                },
                                onClick = {
                                    state.selectSelectedTokenMarkerAxis(axis)
                                    markerMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Circular tokens use a radial orientation marker.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            HorizontalDivider()

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

private val rotationPresets = listOf(
    0.0,
    45.0,
    90.0,
    135.0,
    180.0,
    225.0,
    270.0,
    315.0,
)

private fun menuChoice(selected: Boolean, label: String): String =
    if (selected) "✓ $label" else label

private fun nearlyEqual(first: Double, second: Double): Boolean =
    abs(first - second) < 0.000_001

private fun formatDecimal(value: Double): String =
    if (nearlyEqual(value, value.roundToInt().toDouble())) {
        value.roundToInt().toString()
    } else {
        value.toString().trimEnd('0').trimEnd('.')
    }

private fun formatRgbHex(argb: Long): String =
    "#" + (argb and 0xFFFFFFL)
        .toString(radix = 16)
        .uppercase()
        .padStart(6, '0')
