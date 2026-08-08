package com.adoodguy.androidvtt.tabletop

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun TabletopMapHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val pickerRequest = TabletopMapStore.imagePickerRequest
    var lastHandledPickerRequest by rememberSaveable { mutableStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // TabletopMapStore also tolerates providers without persistable grants.
            }
            TabletopMapStore.importImage(
                uri = uri,
                aspectRatio = readMapImageAspectRatio(context, uri),
            )
        }
    }

    LaunchedEffect(pickerRequest) {
        if (pickerRequest > lastHandledPickerRequest) {
            lastHandledPickerRequest = pickerRequest
            launcher.launch(arrayOf("image/*"))
        }
    }

    Box(Modifier.fillMaxSize()) {
        content()

        if (TabletopMapStore.settingsVisible) {
            MapSettingsPanel()
        }

        if (TabletopMapStore.alignmentVisible) {
            MapAlignmentAssistantPanel()
        }
    }
}

@Composable
private fun BoxScope.MapSettingsPanel() {
    val configuration = TabletopMapStore.configuration
    var widthText by remember(configuration.widthCells) {
        mutableStateOf(formatMapNumber(configuration.widthCells))
    }
    var heightText by remember(configuration.heightCells) {
        mutableStateOf(formatMapNumber(configuration.heightCells))
    }
    var centerXText by remember(configuration.centerX) {
        mutableStateOf(formatMapNumber(configuration.centerX))
    }
    var centerYText by remember(configuration.centerY) {
        mutableStateOf(formatMapNumber(configuration.centerY))
    }
    var rotationText by remember(configuration.rotationDegrees) {
        mutableStateOf(formatMapNumber(configuration.rotationDegrees))
    }
    var errorText by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(12.dp)
            .widthIn(min = 260.dp, max = 330.dp)
            .heightIn(max = 560.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Map settings", style = MaterialTheme.typography.titleSmall)
            Text(
                "The map remains beneath the grid, drawings, measurements, and tokens.",
                style = MaterialTheme.typography.bodySmall,
            )

            Button(onClick = TabletopMapStore::requestImagePicker) {
                Text("Replace image")
            }

            Button(onClick = TabletopMapStore::openAlignmentAssistant) {
                Text("Alignment assistant")
            }

            OutlinedTextField(
                value = widthText,
                onValueChange = {
                    widthText = it
                    errorText = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Width in cells") },
                singleLine = true,
            )
            OutlinedTextField(
                value = heightText,
                onValueChange = {
                    heightText = it
                    errorText = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Height in cells") },
                singleLine = true,
            )
            OutlinedTextField(
                value = centerXText,
                onValueChange = {
                    centerXText = it
                    errorText = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Center X (cells)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = centerYText,
                onValueChange = {
                    centerYText = it
                    errorText = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Center Y (cells)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = rotationText,
                onValueChange = {
                    rotationText = it
                    errorText = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Rotation in degrees") },
                singleLine = true,
            )

            errorText?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Button(
                onClick = {
                    val width = widthText.toDoubleOrNull()
                    val height = heightText.toDoubleOrNull()
                    val centerX = centerXText.toDoubleOrNull()
                    val centerY = centerYText.toDoubleOrNull()
                    val rotation = rotationText.toDoubleOrNull()
                    if (
                        width != null &&
                        height != null &&
                        centerX != null &&
                        centerY != null &&
                        rotation != null &&
                        TabletopMapStore.updateGeometry(
                            widthCells = width,
                            heightCells = height,
                            centerX = centerX,
                            centerY = centerY,
                            rotationDegrees = rotation,
                        )
                    ) {
                        errorText = null
                    } else {
                        errorText = "Use positive width/height values and numeric position/rotation values."
                    }
                },
            ) {
                Text("Apply map geometry")
            }

            Button(
                onClick = {
                    centerXText = "0"
                    centerYText = "0"
                    rotationText = "0"
                    TabletopMapStore.updateGeometry(
                        widthCells = configuration.widthCells,
                        heightCells = configuration.heightCells,
                        centerX = 0.0,
                        centerY = 0.0,
                        rotationDegrees = 0.0,
                    )
                    errorText = null
                },
            ) {
                Text("Reset position and rotation")
            }

            Button(
                onClick = {
                    TabletopMapStore.removeMap()
                    errorText = null
                },
            ) {
                Text("Remove map")
            }

            Button(onClick = TabletopMapStore::closeSettings) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun BoxScope.MapAlignmentAssistantPanel() {
    Card(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(12.dp)
            .widthIn(min = 280.dp, max = 540.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Map alignment assistant", style = MaterialTheme.typography.titleSmall)
            Text(
                "1. Use two fingers to pan/zoom for precision. Drag with one finger anywhere on the " +
                    "tabletop to move the yellow crosshair over the source image. Releasing places that " +
                    "chosen map point on the nearest app-grid anchor.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "2. Use the dedicated controller around the crosshair: drag the orange square toward or " +
                    "away from the crosshair to scale the whole map proportionally. Use the orange ruler " +
                    "and example grid to match several printed cells at once.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "3. Drag the purple circular handle around its ring to rotate the map about the crosshair. " +
                    "Choose Done when the printed grid matches. Future snapped map movement uses this " +
                    "crosshair rather than the image center.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = TabletopMapStore::resetSnapAnchorToCenter) {
                    Text("Crosshair to center")
                }
                Button(onClick = TabletopMapStore::finishAlignment) {
                    Text("Done")
                }
                Button(onClick = TabletopMapStore::cancelAlignment) {
                    Text("Cancel")
                }
            }
        }
    }
}

private fun formatMapNumber(value: Double): String {
    val integer = value.toLong()
    return if (value == integer.toDouble()) integer.toString() else value.toString()
}
