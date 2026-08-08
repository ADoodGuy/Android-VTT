package com.adoodguy.androidvtt.tabletop

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = DiceRollerStore::openHistory) {
                        Text("History (${DiceRollerStore.history.size})")
                    }
                    TextButton(onClick = DiceRollerStore::closePanel) {
                        Text("Close")
                    }
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
                DiceRollerMode.CLUSTER -> ClusterEditor()
                DiceRollerMode.SINGLE -> SingleEditor()
            }

            DiceRollerStore.validationMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (DiceRollerStore.resultVisible) DiceResultPopup()
    if (DiceRollerStore.historyVisible) DiceHistoryPopup()
}

@Composable
private fun ClusterEditor() {
    var presetMenuVisible by remember { mutableStateOf(false) }
    var presetEditorVisible by remember { mutableStateOf(false) }
    var editingPresetId by remember { mutableStateOf<Long?>(null) }
    var presetName by remember { mutableStateOf("") }

    Text(
        "Fast dice pools using d2 through d12. Results and rerolls open in a separate result window.",
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

    PresetControls(
        count = DiceRollerStore.clusterPresets.size,
        menuVisible = presetMenuVisible,
        onToggleMenu = { presetMenuVisible = !presetMenuVisible },
        onSaveNew = {
            editingPresetId = null
            presetName = defaultClusterPresetName()
            presetEditorVisible = true
        },
    )

    if (presetMenuVisible) {
        ClusterPresetMenu(
            onEdit = { id, name ->
                DiceRollerStore.loadClusterPreset(id)
                editingPresetId = id
                presetName = name
                presetEditorVisible = true
            },
            onDeleted = { deletedId ->
                if (editingPresetId == deletedId) {
                    editingPresetId = null
                    presetEditorVisible = false
                }
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
        )
    }
}

@Composable
private fun SingleEditor() {
    var presetMenuVisible by remember { mutableStateOf(false) }
    var presetEditorVisible by remember { mutableStateOf(false) }
    var editingPresetId by remember { mutableStateOf<Long?>(null) }
    var presetName by remember { mutableStateOf("") }

    Text(
        "Each dice set has a +/− contribution operator. A − set subtracts the rolled subtotal itself. Fixed numeric modifiers remain separate below.",
        style = MaterialTheme.typography.bodySmall,
    )

    Text("Dice sets", style = MaterialTheme.typography.titleSmall)
    DiceRollerStore.singleSets.forEachIndexed { index, set ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Set ${index + 1}", style = MaterialTheme.typography.labelLarge)
                Button(onClick = { DiceRollerStore.toggleSingleSetOperation(index) }) {
                    Text(set.operation.symbol)
                }
                OutlinedTextField(
                    value = set.countText,
                    onValueChange = { DiceRollerStore.updateSingleSetCount(index, it) },
                    modifier = Modifier.width(86.dp),
                    label = { Text("Dice") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = set.sidesText,
                    onValueChange = { DiceRollerStore.updateSingleSetSides(index, it) },
                    modifier = Modifier.width(86.dp),
                    label = { Text("Sides") },
                    prefix = { Text("d") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    if (set.operation == DiceModifierOperation.ADD) "Add roll" else "Subtract roll",
                    style = MaterialTheme.typography.labelMedium,
                )
                if (DiceRollerStore.singleSets.size > 1) {
                    TextButton(onClick = { DiceRollerStore.removeSingleSet(index) }) {
                        Text("Remove")
                    }
                }
            }
        }
    }

    Button(onClick = DiceRollerStore::addSingleSet) {
        Text("Add dice set")
    }

    HorizontalDivider()
    Text("Fixed modifiers", style = MaterialTheme.typography.titleSmall)

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
                modifier = Modifier.width(110.dp),
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
        PresetControls(
            count = DiceRollerStore.singlePresets.size,
            menuVisible = presetMenuVisible,
            onToggleMenu = { presetMenuVisible = !presetMenuVisible },
            onSaveNew = {
                editingPresetId = null
                presetName = defaultSinglePresetName()
                presetEditorVisible = true
            },
        )
    }

    if (presetMenuVisible) {
        SinglePresetMenu(
            onEdit = { id, name ->
                DiceRollerStore.loadSinglePreset(id)
                editingPresetId = id
                presetName = name
                presetEditorVisible = true
            },
            onDeleted = { deletedId ->
                if (editingPresetId == deletedId) {
                    editingPresetId = null
                    presetEditorVisible = false
                }
            },
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
        )
    }
}

@Composable
private fun PresetControls(
    count: Int,
    menuVisible: Boolean,
    onToggleMenu: () -> Unit,
    onSaveNew: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onToggleMenu) {
            Text(if (menuVisible) "Hide presets" else "Presets ($count)")
        }
        Button(onClick = onSaveNew) {
            Text("Save preset")
        }
    }
}

@Composable
private fun ClusterPresetMenu(
    onEdit: (Long, String) -> Unit,
    onDeleted: (Long) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Cluster presets", style = MaterialTheme.typography.titleSmall)
            if (DiceRollerStore.clusterPresets.isEmpty()) {
                Text("No Cluster presets saved.", style = MaterialTheme.typography.bodySmall)
            }
            DiceRollerStore.clusterPresets.forEach { preset ->
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${preset.name} • ${preset.count}d${preset.sides}",
                        modifier = Modifier.widthIn(min = 150.dp),
                    )
                    Button(onClick = { DiceRollerStore.quickRollClusterPreset(preset.id) }) {
                        Text("Roll")
                    }
                    TextButton(onClick = { onEdit(preset.id, preset.name) }) {
                        Text("Edit")
                    }
                    TextButton(
                        onClick = {
                            DiceRollerStore.deleteClusterPreset(preset.id)
                            onDeleted(preset.id)
                        },
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun SinglePresetMenu(
    onEdit: (Long, String) -> Unit,
    onDeleted: (Long) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Single presets", style = MaterialTheme.typography.titleSmall)
            if (DiceRollerStore.singlePresets.isEmpty()) {
                Text("No Single presets saved.", style = MaterialTheme.typography.bodySmall)
            }
            DiceRollerStore.singlePresets.forEach { preset ->
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${preset.name} • ${formatSinglePreset(preset)}",
                        modifier = Modifier.widthIn(min = 190.dp),
                    )
                    Button(onClick = { DiceRollerStore.quickRollSinglePreset(preset.id) }) {
                        Text("Roll")
                    }
                    TextButton(onClick = { onEdit(preset.id, preset.name) }) {
                        Text("Edit")
                    }
                    TextButton(
                        onClick = {
                            DiceRollerStore.deleteSinglePreset(preset.id)
                            onDeleted(preset.id)
                        },
                    ) {
                        Text("Delete")
                    }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) { Text(saveLabel) }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun BoxScope.DiceResultPopup() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(104f)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f))
            .clickable(onClick = DiceRollerStore::closeResult),
    )

    Card(
        modifier = Modifier
            .align(Alignment.Center)
            .zIndex(105f)
            .padding(16.dp)
            .widthIn(min = 290.dp, max = 540.dp)
            .heightIn(max = 650.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Roll result", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = DiceRollerStore::closeResult) { Text("Close") }
            }

            when (DiceRollerStore.mode) {
                DiceRollerMode.CLUSTER -> ClusterResultContent()
                DiceRollerMode.SINGLE -> SingleResultContent()
            }
        }
    }
}

@Composable
private fun ClusterResultContent() {
    val roll = DiceRollerStore.currentClusterRoll ?: return
    var selectedFace by remember(roll.id) { mutableStateOf<Int?>(null) }
    val counts = roll.countByFace()
    val maxCount = (1..roll.sides).maxOfOrNull { counts[it] ?: 0 }?.coerceAtLeast(1) ?: 1

    Text(
        "${roll.results.size}d${roll.sides} • ${roll.operationLabel} • total ${roll.results.sum()}",
        style = MaterialTheme.typography.titleMedium,
    )
    Text("Tap anywhere on a result row for reroll options.", style = MaterialTheme.typography.bodySmall)

    for (face in 1..roll.sides) {
        val count = counts[face] ?: 0
        val fraction = count.toFloat() / maxCount.toFloat()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedFace = face }
                .padding(vertical = 3.dp),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(face.toString(), modifier = Modifier.width(28.dp), fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(22.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (count > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(22.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
                        )
                    }
                }
                Text(
                    "$count ${if (count == 1) "die" else "dice"}",
                    modifier = Modifier.width(70.dp),
                )
            }
        }
    }

    selectedFace?.let { face ->
        HorizontalDivider()
        Text("Reroll from result $face", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    DiceRollerStore.rerollCluster(face, ClusterRerollRule.EXACT)
                    selectedFace = null
                },
            ) { Text("Only $face") }
            Button(
                onClick = {
                    DiceRollerStore.rerollCluster(face, ClusterRerollRule.OR_LOWER)
                    selectedFace = null
                },
            ) { Text("$face or lower") }
            Button(
                onClick = {
                    DiceRollerStore.rerollCluster(face, ClusterRerollRule.OR_HIGHER)
                    selectedFace = null
                },
            ) { Text("$face or higher") }
        }
    }
}

@Composable
private fun SingleResultContent() {
    val roll = DiceRollerStore.currentSingleRoll ?: return
    val context = LocalContext.current
    var copied by remember(roll.id) { mutableStateOf(false) }

    Text(roll.expression, style = MaterialTheme.typography.labelLarge)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("Dice result", roll.kept.total.toString()),
                )
                copied = true
            },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${roll.kept.total}", style = MaterialTheme.typography.headlineLarge)
            Text(
                if (copied) "Copied to clipboard" else "Tap the final result to copy",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (roll.keepMode != DiceKeepMode.NORMAL && roll.second != null) {
        Text(
            "${roll.keepMode.label}: ${roll.first.total} vs ${roll.second.total} • kept attempt ${roll.keptAttempt}",
            style = MaterialTheme.typography.bodyMedium,
        )
        AttemptCard("Attempt 1", roll.first, roll.keptAttempt == 1)
        AttemptCard("Attempt 2", roll.second, roll.keptAttempt == 2)
    } else {
        AttemptCard("Roll details", roll.first, true)
    }
}

@Composable
private fun AttemptCard(title: String, attempt: SingleDiceAttempt, kept: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(if (kept) "$title • kept" else title, style = MaterialTheme.typography.titleSmall)
            attempt.sets.forEachIndexed { index, set ->
                val visible = set.results.take(16).joinToString(", ")
                val suffix = if (set.results.size > 16) ", …" else ""
                val signedContribution = if (set.contribution >= 0) "+${set.contribution}" else set.contribution.toString()
                Text(
                    "Set ${index + 1}: ${set.operation.symbol} ${set.count}d${set.sides} [$visible$suffix] = $signedContribution",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (attempt.modifiers.isNotEmpty()) {
                val fixed = attempt.modifiers.joinToString(" ") {
                    "${it.operation.symbol} ${it.value}"
                }
                Text("Fixed modifiers: $fixed", style = MaterialTheme.typography.bodySmall)
            }
            Text("Total ${attempt.total}", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun BoxScope.DiceHistoryPopup() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(109f)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f))
            .clickable(onClick = DiceRollerStore::closeHistory),
    )

    Card(
        modifier = Modifier
            .align(Alignment.Center)
            .zIndex(110f)
            .padding(16.dp)
            .widthIn(min = 280.dp, max = 520.dp)
            .heightIn(max = 650.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recent rolls", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = DiceRollerStore::closeHistory) { Text("Close") }
            }

            if (DiceRollerStore.history.isEmpty()) {
                Text("No rolls yet.", style = MaterialTheme.typography.bodySmall)
            }

            DiceRollerStore.history.forEachIndexed { index, entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "${index + 1}. ${historyTitle(entry)}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(historyDetail(entry), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun defaultClusterPresetName(): String =
    "${DiceRollerStore.clusterCountText}d${DiceRollerStore.clusterSidesText}"

private fun defaultSinglePresetName(): String = formatDraftExpression().take(40)

private fun formatDraftExpression(): String = buildString {
    DiceRollerStore.singleSets.forEachIndexed { index, set ->
        appendDiceSetTerm(
            index = index,
            operation = set.operation,
            term = "${set.countText.ifBlank { "?" }}d${set.sidesText.ifBlank { "?" }}",
        )
    }
    DiceRollerStore.singleModifiers.forEach { modifier ->
        append(" ${modifier.operation.symbol} ${modifier.valueText.ifBlank { "0" }}")
    }
}

private fun formatSinglePreset(preset: SingleDicePreset): String = buildString {
    preset.sets.forEachIndexed { index, set ->
        appendDiceSetTerm(index, set.operation, "${set.count}d${set.sides}")
    }
    preset.modifiers.forEach { append(" ${it.operation.symbol} ${it.value}") }
    if (preset.keepMode != DiceKeepMode.NORMAL) append(" • ${preset.keepMode.label}")
}

private fun StringBuilder.appendDiceSetTerm(
    index: Int,
    operation: DiceModifierOperation,
    term: String,
) {
    when {
        index == 0 && operation == DiceModifierOperation.ADD -> append(term)
        index == 0 -> append("− $term")
        operation == DiceModifierOperation.ADD -> append(" + $term")
        else -> append(" − $term")
    }
}

private fun historyTitle(entry: DiceHistoryEntry): String = when (entry) {
    is ClusterDiceRoll -> "Cluster • ${entry.results.size}d${entry.sides} • ${entry.operationLabel}"
    is SingleDiceRoll -> "Single • ${entry.expression} • total ${entry.kept.total}"
}

private fun historyDetail(entry: DiceHistoryEntry): String = when (entry) {
    is ClusterDiceRoll -> (1..entry.sides).joinToString(" • ") { face ->
        "$face: ${entry.countByFace()[face] ?: 0}"
    }
    is SingleDiceRoll -> if (entry.keepMode == DiceKeepMode.NORMAL || entry.second == null) {
        compactAttempt(entry.first)
    } else {
        "${entry.keepMode.label}: ${entry.first.total} / ${entry.second.total}; kept ${entry.kept.total}"
    }
}

private fun compactAttempt(attempt: SingleDiceAttempt): String {
    val sets = attempt.sets.mapIndexed { index, set ->
        buildString {
            appendDiceSetTerm(index, set.operation, "${set.count}d${set.sides}=${set.rolledSubtotal}")
        }
    }.joinToString("")
    val fixed = attempt.modifiers.joinToString(" ") { "${it.operation.symbol}${it.value}" }
    return if (fixed.isBlank()) sets else "$sets • $fixed"
}