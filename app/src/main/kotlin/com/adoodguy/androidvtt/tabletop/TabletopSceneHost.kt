package com.adoodguy.androidvtt.tabletop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun TabletopSceneHost(content: @Composable () -> Unit) {
    var managerVisible by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        content()

        Button(
            onClick = { managerVisible = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 68.dp)
                .zIndex(80f),
        ) {
            Text("Scenes: ${TabletopSceneStore.activeSceneName}")
        }

        if (managerVisible) {
            SceneManagerPanel(onClose = { managerVisible = false })
        }

        if (DiceToolUiStore.active && !DiceRollerStore.panelVisible) {
            Button(
                onClick = DiceRollerStore::openPanel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 68.dp)
                    .zIndex(99f),
            ) {
                Text("Open dice roller")
            }
        }

        DiceRollerOverlay()
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SceneManagerPanel(
    onClose: () -> Unit,
) {
    val scenes = TabletopSceneStore.scenes
    val activeId = TabletopSceneStore.activeSceneId
    val activeName = TabletopSceneStore.activeSceneName
    var renameText by remember(activeId, activeName) { mutableStateOf(activeName) }
    var renameError by remember(activeId) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(activeId) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(90f)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f))
            .clickable(onClick = onClose),
    ) {}

    Card(
        modifier = Modifier
            .align(Alignment.Center)
            .zIndex(91f)
            .padding(16.dp)
            .widthIn(min = 280.dp, max = 360.dp),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Scenes", style = MaterialTheme.typography.titleMedium)
            Text(
                "Switching scenes autosaves the current tabletop first. Maps, tokens, tools, notes, grid settings, and camera state belong to the selected scene.",
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider()

            scenes.forEach { scene ->
                FilterChip(
                    selected = scene.id == activeId,
                    onClick = {
                        if (TabletopSceneStore.switchTo(scene.id)) {
                            renameText = TabletopSceneStore.activeSceneName
                            renameError = null
                            confirmDelete = false
                        }
                    },
                    label = { Text(scene.name) },
                )
            }

            HorizontalDivider()

            OutlinedTextField(
                value = renameText,
                onValueChange = {
                    renameText = it
                    renameError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Active scene name") },
                singleLine = true,
                isError = renameError != null,
            )
            renameError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Button(
                onClick = {
                    if (TabletopSceneStore.renameActiveScene(renameText)) {
                        renameText = TabletopSceneStore.activeSceneName
                        renameError = null
                    } else {
                        renameError = "Enter a scene name."
                    }
                },
            ) {
                Text("Rename")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        TabletopSceneStore.createScene()
                        renameText = TabletopSceneStore.activeSceneName
                        renameError = null
                        confirmDelete = false
                    },
                ) {
                    Text("New blank")
                }
                Button(
                    onClick = {
                        TabletopSceneStore.duplicateActiveScene()
                        renameText = TabletopSceneStore.activeSceneName
                        renameError = null
                        confirmDelete = false
                    },
                ) {
                    Text("Duplicate")
                }
            }

            if (scenes.size > 1) {
                HorizontalDivider()
                if (confirmDelete) {
                    Text(
                        "Delete ‘$activeName’? This removes the saved scene from this device.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                TabletopSceneStore.deleteActiveScene()
                                renameText = TabletopSceneStore.activeSceneName
                                renameError = null
                                confirmDelete = false
                            },
                        ) {
                            Text("Confirm delete")
                        }
                        TextButton(onClick = { confirmDelete = false }) {
                            Text("Cancel")
                        }
                    }
                } else {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete active scene")
                    }
                }
            }

            HorizontalDivider()
            Button(onClick = onClose) {
                Text("Close")
            }
        }
    }
}
