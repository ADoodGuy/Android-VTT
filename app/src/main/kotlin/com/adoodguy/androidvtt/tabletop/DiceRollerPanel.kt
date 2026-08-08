package com.adoodguy.androidvtt.tabletop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun BoxScope.DiceRollerOverlay() {
    if (!DiceRollerStore.panelVisible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.34f))
            .clickable(onClick = DiceRollerStore::closePanel),
    )

    Card(
        modifier = Modifier
            .align(Alignment.Center)
            .zIndex(101f)
            .padding(12.dp)
            .widthIn(min = 300.dp, max = 560.dp)
            .heightIn(max = 700.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Dice roller", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = DiceRollerStore::closePanel) {
                    Text("Close")
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DiceRollerMode.entries.forEach { mode ->
                    FilterChip(
                        selected = DiceRollerStore.mode == mode,
                        onClick = { DiceRollerStore.selectMode(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }

            when (DiceRollerStore.mode) {
                DiceRollerMode.CLUSTER -> ClusterRollerContent()
                DiceRollerMode.SINGLE -> SingleRollerContent()
            }

            DiceRollerStore.validationMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            HorizontalDivider()
            DiceHistoryContent()
        }
    }
}

@Composable
private fun ClusterRollerContent() {
    var selectedFace by remember(DiceRollerStore.currentClusterRoll?.id) {
        mutableStateOf<Int?>(null)
    }
    var presetMenuExpanded by remember { mutableStateOf(false) }
    var presetEditorVisible by remember { mutableStateOf(false) }
    var editingPresetId by remember { mutableStateOf<Long?>(null) }
    var presetName by remember { mutableStateOf("") }

    Text(
        "Fast dice pools. Use d2 through d12, then tap a result bucket to reroll matching dice.",
        style = MaterialTheme.typography.bodySmall,
    )

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = DiceRollerStore.clusterCountText,
            onValueChange = DiceRollerStore::updateClusterCount,
            modifier = Modifier.width(100.dp),
            label = { Text("Dice") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = DiceRollerStore.clusterSidesText,
            onValueChange = DiceRollerStore::updateClusterSides,
            modifier = Modifier.width(100.dp),
            label = { Text("Sides") },
            prefix = { Text("d") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Button(onClick = { DiceRollerStore.rollCluster() }) {
            Text("Roll cluster")
        }
    }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Button(onClick = { presetMenuExpanded = true }) {
                Text("Presets (${DiceRollerStore.clusterPresets.size})")
            }
            DropdownMenu(
                expanded = presetMenuExpanded,
                onDismissRequest = { presetMenuExpanded = false },
            ) {
                if (DiceRollerStore.clusterPresets.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No Cluster presets saved") },
                        onClick = { presetMenuExpanded = false },
                    )
                }
                DiceRollerStore.clusterPresets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text("Roll ${preset.name} — ${preset.count}d${preset.sides}") },
                        onClick = {
                            DiceRollerStore.quickRollClusterPreset(preset.id)
                            selectedFace = null
                            presetMenuExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Edit ${preset.name}") },
                        onClick = {
                            DiceRollerStore.loadClusterPreset(preset.id)
                            editingPresetId = preset.id
                            presetName = preset.name
                            presetEditorVisible = true
                            presetMenuExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete ${preset.name}") },
                        onClick = {
                            DiceRollerStore.deleteClusterPreset(preset.id)
                            if (editingPresetId == preset.id) {
                                editingPresetId = null
                                presetEditorVisible = false
                            }
                            presetMenuExpanded = false
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
        Button(
            onClick = {
                editingPresetId = null
                presetName = defaultClusterPresetName()
                presetEditorVisible = true
            },
        ) {
            Text("Save preset")
        }
    }

    if (presetEditorVisible) {
        PresetNameEditor(
            title = if (editingPresetId == null) "Save Cluster preset" else "Edit Cluster preset",
            name = presetName,
            onNameChange = { presetName = it },
            saveLabel = if (editingPresetId == null) "Save" else "Save changes",
            onSave = {
                if (DiceRollerStore.saveClusterPreset(presetName, editingPresetId) != null) {
                    presetEditorVisible = false
                    editingPresetId = null
                }
            },
            onCancel = {
                presetEditorVisible = false
                editingPresetId = null
            },
            onDelete = editingPresetId?.let { id ->
                {
                    DiceRollerStore.deleteClusterPreset(id)
                    presetEditorVisible = false
                    editingPresetId = null
                }
            },
        )
    }

    DiceRollerStore.currentClusterRoll?.let { roll ->
        Text(
            text = "${roll.results.size}d${roll.sides} • ${roll.operationLabel} • total ${roll.results.sum()}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text("Results — tap a bucket to reroll", style = MaterialTheme.typography.labelMedium)

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            roll.countByFace().forEach { (face, count) ->
                AssistChip(
                    onClick = { selectedFace = face },
                    label = { Text("$face: $count ${if (count == 1) "die" else "dice"}") },
                )
            }
        }

        selectedFace?.let { face ->
            Text(
                "Reroll dice currently showing $face:",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        DiceRollerStore.rerollCluster(face, ClusterRerollRule.EXACT)
                        selectedFace = null
                    },
                ) {
                    Text("Only $face")
                }
                Button(
                    onClick = {
                        DiceRollerStore.rerollCluster(face, ClusterRerollRule.OR_LOWER)
                        selectedFace = null
                    },
                ) {
                    Text("$face or lower")
                }
                Button(
                    onClick = {
                        DiceRollerStore.rerollCluster(face, ClusterRerollRule.OR_HIGHER)
                        selectedFace = null
                    },
                ) {
                    Text("$face or higher")
                }
            }
        }
    }
}

@Composable
private fun SingleRollerContent() {
    var presetMenuExpanded by remember { mutableStateOf(false) }
    var presetEditorVisible by remember { mutableStateOf(false) }
    var editingPresetId by remember { mutableStateOf<Long?>(null) }
    var presetName by remember { mutableStateOf("") }

    Text(
        "Build one expression from multiple dice sets. Advantage/Disadvantage rolls the complete expression twice and keeps the higher/lower total.",
        style = MaterialTheme.typography.bodySmall,
    )

    DiceRollerStore.singleSets.forEachIndexed { index, set ->
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Set ${index + 1}", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = set.countText,
                onValueChange = { DiceRollerStore.updateSingleSetCount(index, it) },
                modifier = Modifier.width(92.dp),
                label = { Text("Dice") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = set.sidesText,
                onValueChange = { DiceRollerStore.updateSingleSetSides(index, it) },
                modifier = Modifier.width(92.dp),
                label = { Text("Sides") },
                prefix = { Text("d") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            if (DiceRollerStore.singleSets.size > 1) {
                TextButton(onClick = { DiceRollerStore.removeSingleSet(index) }) {
                    Text("Remove")
                }
            }
        }
    }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = DiceRollerStore::addSingleSet) {
            Text("Add dice set")
        }
        OutlinedTextField(
            value = DiceRollerStore.singleModifierText,
            onValueChange = DiceRollerStore::updateSingleModifier,
            modifier = Modifier.width(120.dp),
            label = { Text("Modifier") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
    }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DiceKeepMode.entries.forEach { mode ->
            FilterChip(
                selected = DiceRollerStore.keepMode == mode,
                onClick = { DiceRollerStore.selectKeepMode(mode) },
                label = { Text(mode.label) },
            )
        }
    }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { DiceRollerStore.rollSingle() }) {
            Text("Roll expression")
        }
        Box {
            Button(onClick = { presetMenuExpanded = true }) {
                Text("Presets (${DiceRollerStore.singlePresets.size})")
            }
            DropdownMenu(
                expanded = presetMenuExpanded,
                onDismissRequest = { presetMenuExpanded = false },
            ) {
                if (DiceRollerStore.singlePresets.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No Single presets saved") },
                        onClick = { presetMenuExpanded = false },
                    )
                }
                DiceRollerStore.singlePresets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text("Roll ${preset.name} — ${formatSinglePreset(preset)}") },
                        onClick = {
                            DiceRollerStore.quickRollSinglePreset(preset.id)
                            presetMenuExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Edit ${preset.name}") },
                        onClick = {
                            DiceRollerStore.loadSinglePreset(preset.id)
                            editingPresetId = preset.id
                            presetName = preset.name
                            presetEditorVisible = true
                            presetMenuExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete ${preset.name}") },
                        onClick = {
                            DiceRollerStore.deleteSinglePreset(preset.id)
                            if (editingPresetId == preset.id) {
                                editingPresetId = null
                                presetEditorVisible = false
                            }
                            presetMenuExpanded = false
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
        Button(
            onClick = {
                editingPresetId = null
                presetName = defaultSinglePresetName()
                presetEditorVisible = true
            },
        ) {
            Text("Save preset")
        }
    }

    if (presetEditorVisible) {
        PresetNameEditor(
            title = if (editingPresetId == null) "Save Single preset" else "Edit Single preset",
            name = presetName,
            onNameChange = { presetName = it },
            saveLabel = if (editingPresetId == null) "Save" else "Save changes",
            onSave = {
                if (DiceRollerStore.saveSinglePreset(presetName, editingPresetId) != null) {
                    presetEditorVisible = false
                    editingPresetId = null
                }
            },
            onCancel = {
                presetEditorVisible = false
                editingPresetId = null
            },
            onDelete = editingPresetId?.let { id ->
                {
                    DiceRollerStore.deleteSinglePreset(id)
                    presetEditorVisible = false
                    editingPresetId = null
                }
            },
        )
    }

    DiceRollerStore.currentSingleRoll?.let { roll ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(roll.expression, style = MaterialTheme.typography.labelLarge)
                Text(
                    "Total ${roll.kept.total}",
                    style = MaterialTheme.typography.headlineMedium,
                )
                if (roll.keepMode != DiceKeepMode.NORMAL && roll.second != null) {
                    Text(
                        "${roll.keepMode.label}: ${roll.first.total} vs ${roll.second.total} • kept attempt ${roll.keptAttempt}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Attempt 1: ${formatAttempt(roll.first)}", style = MaterialTheme.typography.bodySmall)
                    Text("Attempt 2: ${formatAttempt(roll.second)}", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(formatAttempt(roll.first), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PresetNameEditor(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    saveLabel: String,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Preset name") },
                singleLine = true,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onSave) {
                    Text(saveLabel)
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
                onDelete?.let { delete ->
                    TextButton(onClick = delete) {
                        Text("Delete preset")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiceHistoryContent() {
    Text("Recent rolls", style = MaterialTheme.typography.titleMedium)
    if (DiceRollerStore.history.isEmpty()) {
        Text("No rolls yet.", style = MaterialTheme.typography.bodySmall)
        return
    }

    DiceRollerStore.history.forEachIndexed { index, entry ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("${index + 1}. ${historyTitle(entry)}", style = MaterialTheme.typography.labelLarge)
                Text(historyDetail(entry), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun defaultClusterPresetName(): String =
    "${DiceRollerStore.clusterCountText}d${DiceRollerStore.clusterSidesText}"

private fun defaultSinglePresetName(): String {
    val dice = DiceRollerStore.singleSets.joinToString(" + ") { draft ->
        "${draft.countText.ifBlank { "?" }}d${draft.sidesText.ifBlank { "?" }}"
    }
    val modifier = DiceRollerStore.singleModifierText.toIntOrNull() ?: 0
    val expression = when {
        modifier > 0 -> "$dice + $modifier"
        modifier < 0 -> "$dice - ${-modifier}"
        else -> dice
    }
    return expression.take(40)
}

private fun formatSinglePreset(preset: SingleDicePreset): String {
    val dice = preset.sets.joinToString(" + ") { "${it.count}d${it.sides}" }
    val expression = when {
        preset.modifier > 0 -> "$dice + ${preset.modifier}"
        preset.modifier < 0 -> "$dice - ${-preset.modifier}"
        else -> dice
    }
    return if (preset.keepMode == DiceKeepMode.NORMAL) {
        expression
    } else {
        "$expression • ${preset.keepMode.label}"
    }
}

private fun historyTitle(entry: DiceHistoryEntry): String =
    when (entry) {
        is ClusterDiceRoll -> "Cluster • ${entry.results.size}d${entry.sides} • ${entry.operationLabel}"
        is SingleDiceRoll -> "Single • ${entry.expression} • total ${entry.kept.total}"
    }

private fun historyDetail(entry: DiceHistoryEntry): String =
    when (entry) {
        is ClusterDiceRoll -> entry.countByFace().entries.joinToString(" • ") { (face, count) ->
            "$face: $count"
        }

        is SingleDiceRoll -> if (entry.keepMode == DiceKeepMode.NORMAL || entry.second == null) {
            formatAttempt(entry.first)
        } else {
            "${entry.keepMode.label}: ${entry.first.total} / ${entry.second.total}; kept ${entry.kept.total}"
        }
    }

private fun formatAttempt(attempt: SingleDiceAttempt): String {
    val sets = attempt.sets.joinToString(" + ") { set ->
        val visibleResults = set.results.take(12).joinToString(", ")
        val suffix = if (set.results.size > 12) ", …" else ""
        "${set.count}d${set.sides} [$visibleResults$suffix]"
    }
    return when {
        attempt.modifier > 0 -> "$sets + ${attempt.modifier}"
        attempt.modifier < 0 -> "$sets - ${-attempt.modifier}"
        else -> sets
    }
}
