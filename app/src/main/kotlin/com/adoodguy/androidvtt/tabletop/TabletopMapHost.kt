package com.adoodguy.androidvtt.tabletop

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun TabletopMapHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var panelVisible by remember { mutableStateOf(false) }
    val configuration = TabletopMapStore.configuration

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            TabletopMapStore.importImage(
                uri = uri,
                aspectRatio = readMapImageAspectRatio(context, uri),
            )
            panelVisible = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        content()

        Button(
            onClick = { panelVisible = !panelVisible },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 72.dp),
        ) {
            Text(if (configuration.hasImage) "Map ✓" else "Map")
        }

        if (panelVisible) {
            MapSettingsPanel(
                configuration = configuration,
                onChooseImage = { launcher.launch(arrayOf("image/*")) },
                onClose = { panelVisible = false },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 126.dp),
            )
        }
    }
}

@Composable
private fun MapSettingsPanel(
    configuration: TabletopMapConfiguration,
    onChooseImage: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    var errorText by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier
            .widthIn(min = 260.dp, max = 330.dp)
            .heightIn(max = 520.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Map settings", style = MaterialTheme.typography.titleSmall)
            Text(
                if (configuration.hasImage) {
                    "Image selected. The grid, drawings, measurements, and tokens render above it."
                } else {
                    "Choose an image from this device to use as the tabletop background."
                },
                style = MaterialTheme.typography.bodySmall,
            )

            Button(onClick = onChooseImage) {
                Text(if (configuration.hasImage) "Replace image" else "Choose image")
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
                    if (
                        width != null &&
                        height != null &&
                        centerX != null &&
                        centerY != null &&
                        TabletopMapStore.updateGeometry(width, height, centerX, centerY)
                    ) {
                        errorText = null
                    } else {
                        errorText = "Use positive width/height values and numeric X/Y coordinates."
                    }
                },
            ) {
                Text("Apply map geometry")
            }

            Button(
                onClick = {
                    centerXText = "0"
                    centerYText = "0"
                    errorText = null
                },
            ) {
                Text("Set center fields to origin")
            }

            if (configuration.hasImage) {
                Button(
                    onClick = {
                        TabletopMapStore.removeMap()
                        errorText = null
                    },
                ) {
                    Text("Remove map")
                }
            }

            Button(onClick = onClose) {
                Text("Close")
            }
        }
    }
}

private fun formatMapNumber(value: Double): String {
    val integer = value.toLong()
    return if (value == integer.toDouble()) integer.toString() else value.toString()
}
