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

    val roll = DiceRollerStore.currentClusterRoll ?: return
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

@Composable
private fun SingleRollerContent() {
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

    Button(onClick = { DiceRollerStore.rollSingle() }) {
        Text("Roll expression")
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
