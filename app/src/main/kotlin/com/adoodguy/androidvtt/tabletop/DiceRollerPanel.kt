package com.adoodguy.androidvtt.tabletop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
    var presetMenuVisible by remember { mutableStateOf(false) }
    var presetEditorVisible by remember { mutableStateOf(false) }
    var editingPresetId by remember { mutableStateOf<Long?>(null) }
    var presetName by remember { mutableStateOf("") }

    Text(
        "Fast dice pools. Use d2 through d12. Tap anywhere on a result row to open its reroll controls.",
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
        Button(onClick = { presetMenuVisible = !presetMenuVisible }) {
            Text(if (presetMenuVisible) "Hide presets" else "Presets (${DiceRollerStore.clusterPresets.size})")
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

    if (presetMenuVisible) {
        ClusterPresetMenu(
            onEdit = { preset ->
                DiceRollerStore.loadClusterPreset(preset.id)
                editingPresetId = preset.id
                presetName = preset.name
                presetEditorVisible = true
            },
            onDelete = { preset ->
                DiceRollerStore.deleteClusterPreset(preset.id)
                if (editingPresetId == preset.id) {
                    editingPresetId = null
                    presetEditorVisible = false
                }
            },
            onRoll = { preset ->
                DiceRollerStore.quickRollClusterPreset(preset.id)
                selectedFace = null
            },
        )
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
        Text("Results", style = MaterialTheme.typography.labelMedium)
        ClusterHistogram(
            roll = roll,
            selectedFace = selectedFace,
            onFaceSelected = { selectedFace = it },
        )

        selectedFace?.let { face ->
            Text(
                "Reroll using result $face as the threshold:",
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
private fun ClusterHistogram(
    roll: ClusterDiceRoll,
    selectedFace: Int?,
    onFaceSelected: (Int) -> Unit,
) {
    val counts = roll.countByFace()
    val maxCount = (1..roll.sides).maxOfOrNull { counts[it] ?: 0 }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (face in 1..roll.sides) {
            val count = counts[face] ?: 0
            val fraction = count.toFloat() / maxCount.toFloat()
            val trackColor = if (selectedFace == face) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(trackColor, RoundedCornerShape(8.dp))
                    .clickable { onFaceSelected(face) },
            ) {
                if (count > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                                RoundedCornerShape(8.dp),
                            ),
                    )
                }
                Text(
                    text = "$face",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "$count ${if (count == 1) "die" else "dice"}",
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun SingleRollerContent() {
    var presetMenuVisible by remember { mutableStateOf(false) }
    var presetEditorVisible by remember { mutableStateOf(false) }
    var editingPresetId by remember { mutableStateOf<Long?>(null) }
    var presetName by remember { mutableStateOf("") }

    Text(
        "Build one expression from multiple dice sets and modifier terms. Advantage/Disadvantage rolls the complete expression twice and keeps the higher/lower total.",
        style = MaterialTheme.typography.bodySmall,
    )

    Text("Dice sets", style = MaterialTheme.typography.labelLarge)
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
    Button(onClick = DiceRollerStore::addSingleSet) {
        Text("Add dice set")
    }

    Text("Modifiers", style = MaterialTheme.typography.labelLarge)
    DiceRollerStore.singleModifiers.forEachIndexed { index, modifier ->
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Modifier ${index + 1}", style = MaterialTheme.typography.labelLarge)
            Button(onClick = { DiceRollerStore.toggleSingleModifierOperation(index) }) {
                Text(modifier.operation.symbol)
            }
            OutlinedTextField(
                value = modifier.valueText,
                onValueChange = { DiceRollerStore.updateSingleModifierValue(index, it) },
                modifier = Modifier.width(120.dp),
                label = { Text("Value") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            if (DiceRollerStore.singleModifiers.size > 1) {
                TextButton(onClick = { DiceRollerStore.removeSingleModifier(index) }) {
                    Text("Remove")
                }
            }
        }
    }
    Button(onClick = DiceRollerStore::addSingleModifier) {
        Text("Add modifier")
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
        Button(onClick = { presetMenuVisible = !presetMenuVisible }) {
            Text(if (presetMenuVisible) "Hide presets" else "Presets (${DiceRollerStore.singlePresets.size})")
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

    if (presetMenuVisible) {
        SinglePresetMenu(
            onEdit = { preset ->
                DiceRollerStore.loadSinglePreset(preset.id)
                editingPresetId = preset.id
                presetName = preset.name
                presetEditorVisible = true
            },
            onDelete = { preset ->
                DiceRollerStore.deleteSinglePreset(preset.id)
                if (editingPresetId == preset.id) {
                    editingPresetId = null
                    presetEditorVisible = false
                }
            },
            onRoll = { preset -> DiceRollerStore.quickRollSinglePreset(preset.id) },
        )
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
private fun ClusterPresetMenu(
    onRoll: (ClusterDicePreset) -> Unit,
    onEdit: (ClusterDicePreset) -> Unit,
    onDelete: (ClusterDicePreset) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                "Cluster presets",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            if (DiceRollerStore.clusterPresets.isEmpty()) {
                Text(
                    "No Cluster presets saved.",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                DiceRollerStore.clusterPresets.forEachIndexed { index, preset ->
                    PresetRow(
                        description = "${preset.name} • ${preset.count}d${preset.sides}",
                        onRoll = { onRoll(preset) },
                        onEdit = { onEdit(preset) },
                        onDelete = { onDelete(preset) },
                    )
                    if (index != DiceRollerStore.clusterPresets.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SinglePresetMenu(
    onRoll: (SingleDicePreset) -> Unit,
    onEdit: (SingleDicePreset) -> Unit,
    onDelete: (SingleDicePreset) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                "Single presets",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            if (DiceRollerStore.singlePresets.isEmpty()) {
                Text(
                    "No Single presets saved.",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                DiceRollerStore.singlePresets.forEachIndexed { index, preset ->
                    PresetRow(
                        description = "${preset.name} • ${formatSinglePreset(preset)}",
                        onRoll = { onRoll(preset) },
                        onEdit = { onEdit(preset) },
                        onDelete = { onDelete(preset) },
                    )
                    if (index != DiceRollerStore.singlePresets.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PresetRow(
    description: String,
    onRoll: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(description, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRoll) { Text("Roll") }
        TextButton(onClick = onEdit) { Text("Edit") }
        TextButton(onClick = onDelete) { Text("Delete") }
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
    val modifiers = DiceRollerStore.singleModifiers.joinToString(" ") { draft ->
        "${draft.operation.symbol} ${draft.valueText.ifBlank { "?" }}"
    }
    return "$dice $modifiers".trim().take(40)
}

private fun formatSinglePreset(preset: SingleDicePreset): String {
    val dice = preset.sets.joinToString(" + ") { "${it.count}d${it.sides}" }
    val modifiers = formatModifiers(preset.modifiers)
    val expression = "$dice$modifiers"
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
    return "$sets${formatModifiers(attempt.modifiers)}"
}

private fun formatModifiers(modifiers: List<DiceModifierSpec>): String =
    buildString {
        modifiers.forEach { modifier ->
            append(" ${modifier.operation.symbol} ${modifier.value}")
        }
    }
