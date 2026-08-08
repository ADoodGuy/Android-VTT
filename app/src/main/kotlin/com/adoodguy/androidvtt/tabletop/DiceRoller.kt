package com.adoodguy.androidvtt.tabletop

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

enum class DiceRollerMode(val label: String) {
    CLUSTER("Cluster"),
    SINGLE("Single"),
}

enum class DiceKeepMode(val label: String) {
    NORMAL("Normal"),
    ADVANTAGE("Advantage"),
    DISADVANTAGE("Disadvantage"),
}

enum class ClusterRerollRule(val label: String) {
    EXACT("this result"),
    OR_LOWER("this result or lower"),
    OR_HIGHER("this result or higher"),
}

enum class DiceModifierOperation(val symbol: String) {
    ADD("+"),
    SUBTRACT("−"),
    ;

    fun toggled(): DiceModifierOperation =
        if (this == ADD) SUBTRACT else ADD
}

data class DiceSetDraft(
    val countText: String = "1",
    val sidesText: String = "20",
)

data class DiceSetSpec(
    val count: Int,
    val sides: Int,
)

data class DiceModifierDraft(
    val operation: DiceModifierOperation = DiceModifierOperation.ADD,
    val valueText: String = "0",
)

data class DiceModifierSpec(
    val operation: DiceModifierOperation,
    val value: Int,
) {
    val signedValue: Int
        get() = if (operation == DiceModifierOperation.ADD) value else -value
}

data class DiceSetOutcome(
    val count: Int,
    val sides: Int,
    val results: List<Int>,
) {
    val subtotal: Int get() = results.sum()
}

data class SingleDiceAttempt(
    val sets: List<DiceSetOutcome>,
    val modifiers: List<DiceModifierSpec>,
) {
    val modifierTotal: Int get() = modifiers.sumOf { it.signedValue }
    val total: Int get() = sets.sumOf { it.subtotal } + modifierTotal
}

data class ClusterDicePreset(
    val id: Long,
    val name: String,
    val count: Int,
    val sides: Int,
)

data class SingleDicePreset(
    val id: Long,
    val name: String,
    val sets: List<DiceSetSpec>,
    val modifiers: List<DiceModifierSpec>,
    val keepMode: DiceKeepMode,
)

sealed interface DiceHistoryEntry {
    val id: Long
}

data class ClusterDiceRoll(
    override val id: Long,
    val sides: Int,
    val results: List<Int>,
    val operationLabel: String = "Roll",
) : DiceHistoryEntry {
    fun countByFace(): Map<Int, Int> = results.groupingBy { it }.eachCount().toSortedMap()
}

data class SingleDiceRoll(
    override val id: Long,
    val expression: String,
    val keepMode: DiceKeepMode,
    val first: SingleDiceAttempt,
    val second: SingleDiceAttempt? = null,
    val keptAttempt: Int = 1,
) : DiceHistoryEntry {
    val kept: SingleDiceAttempt
        get() = if (keptAttempt == 2) second ?: first else first
}

private data class ParsedSingleControls(
    val sets: List<DiceSetSpec>,
    val modifiers: List<DiceModifierSpec>,
)

/**
 * App-wide dice utility state. Dice history and presets are intentionally independent
 * of tabletop scenes so they remain available while switching maps and encounters.
 */
object DiceRollerStore {
    private const val PREFS_NAME = "dice_roller"
    private const val KEY_STATE = "state_json"
    private const val SCHEMA_VERSION = 3
    private const val HISTORY_LIMIT = 5
    private const val MAX_PRESETS_PER_MODE = 50
    private const val MAX_PRESET_NAME_LENGTH = 40
    private const val MAX_CLUSTER_DICE = 500
    private const val MAX_SINGLE_SETS = 8
    private const val MAX_SINGLE_MODIFIERS = 8
    private const val MAX_DICE_PER_SET = 100
    private const val MAX_SINGLE_TOTAL_DICE = 500
    private const val MIN_SIDES = 2
    private const val MAX_SIDES = 100
    private const val MAX_MODIFIER_VALUE = 100_000

    private var appContext: Context? = null
    private var nextHistoryId = 1L
    private var nextPresetId = 1L

    var panelVisible by mutableStateOf(false)
        private set

    var mode by mutableStateOf(DiceRollerMode.CLUSTER)
        private set

    var clusterCountText by mutableStateOf("12")
        private set

    var clusterSidesText by mutableStateOf("6")
        private set

    var currentClusterRoll by mutableStateOf<ClusterDiceRoll?>(null)
        private set

    val singleSets = mutableStateListOf(DiceSetDraft())
    val singleModifiers = mutableStateListOf(DiceModifierDraft())

    var keepMode by mutableStateOf(DiceKeepMode.NORMAL)
        private set

    var currentSingleRoll by mutableStateOf<SingleDiceRoll?>(null)
        private set

    var validationMessage by mutableStateOf<String?>(null)
        private set

    val history = mutableStateListOf<DiceHistoryEntry>()
    val clusterPresets = mutableStateListOf<ClusterDicePreset>()
    val singlePresets = mutableStateListOf<SingleDicePreset>()

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        load()
    }

    fun openPanel() {
        panelVisible = true
    }

    fun closePanel() {
        panelVisible = false
        validationMessage = null
    }

    fun selectMode(newMode: DiceRollerMode) {
        mode = newMode
        validationMessage = null
        persist()
    }

    fun updateClusterCount(text: String) {
        clusterCountText = text.filter { it.isDigit() }.take(4)
        validationMessage = null
    }

    fun updateClusterSides(text: String) {
        clusterSidesText = text.filter { it.isDigit() }.take(3)
        validationMessage = null
    }

    fun rollCluster(): Boolean {
        val spec = parseClusterControls() ?: return false
        return rollClusterSpec(
            count = spec.first,
            sides = spec.second,
            operationLabel = "Roll",
        )
    }

    fun quickRollClusterPreset(presetId: Long): Boolean {
        val preset = clusterPresets.firstOrNull { it.id == presetId } ?: return false
        mode = DiceRollerMode.CLUSTER
        validationMessage = null
        return rollClusterSpec(
            count = preset.count,
            sides = preset.sides,
            operationLabel = "Preset ${preset.name}",
        )
    }

    fun loadClusterPreset(presetId: Long): Boolean {
        val preset = clusterPresets.firstOrNull { it.id == presetId } ?: return false
        mode = DiceRollerMode.CLUSTER
        clusterCountText = preset.count.toString()
        clusterSidesText = preset.sides.toString()
        validationMessage = null
        persist()
        return true
    }

    fun saveClusterPreset(name: String, presetId: Long? = null): Long? {
        val cleanName = cleanPresetName(name) ?: return null
        val spec = parseClusterControls() ?: return null
        val existingIndex = presetId?.let { id -> clusterPresets.indexOfFirst { it.id == id } } ?: -1
        val id = if (existingIndex >= 0) {
            clusterPresets[existingIndex] = clusterPresets[existingIndex].copy(
                name = cleanName,
                count = spec.first,
                sides = spec.second,
            )
            presetId!!
        } else {
            if (clusterPresets.size >= MAX_PRESETS_PER_MODE) {
                validationMessage = "Cluster presets are limited to $MAX_PRESETS_PER_MODE."
                return null
            }
            nextPresetId++.also { newId ->
                clusterPresets += ClusterDicePreset(
                    id = newId,
                    name = cleanName,
                    count = spec.first,
                    sides = spec.second,
                )
            }
        }
        validationMessage = null
        persist()
        return id
    }

    fun deleteClusterPreset(presetId: Long): Boolean {
        val removed = clusterPresets.removeAll { it.id == presetId }
        if (removed) persist()
        return removed
    }

    fun rerollCluster(face: Int, rule: ClusterRerollRule): Boolean {
        val current = currentClusterRoll ?: return false
        if (face !in 1..current.sides) return false

        var rerolled = 0
        val updated = current.results.map { value ->
            val matches = when (rule) {
                ClusterRerollRule.EXACT -> value == face
                ClusterRerollRule.OR_LOWER -> value <= face
                ClusterRerollRule.OR_HIGHER -> value >= face
            }
            if (matches) {
                rerolled += 1
                rollDie(current.sides)
            } else {
                value
            }
        }
        if (rerolled == 0) return false

        val comparison = when (rule) {
            ClusterRerollRule.EXACT -> "$face"
            ClusterRerollRule.OR_LOWER -> "≤$face"
            ClusterRerollRule.OR_HIGHER -> "≥$face"
        }
        val record = ClusterDiceRoll(
            id = nextHistoryId(),
            sides = current.sides,
            results = updated,
            operationLabel = "Reroll $comparison ($rerolled dice)",
        )
        currentClusterRoll = record
        validationMessage = null
        addHistory(record)
        return true
    }

    fun updateSingleSetCount(index: Int, text: String) {
        if (index !in singleSets.indices) return
        singleSets[index] = singleSets[index].copy(
            countText = text.filter { it.isDigit() }.take(3),
        )
        validationMessage = null
    }

    fun updateSingleSetSides(index: Int, text: String) {
        if (index !in singleSets.indices) return
        singleSets[index] = singleSets[index].copy(
            sidesText = text.filter { it.isDigit() }.take(3),
        )
        validationMessage = null
    }

    fun addSingleSet() {
        if (singleSets.size >= MAX_SINGLE_SETS) {
            validationMessage = "A roll can contain up to $MAX_SINGLE_SETS dice sets."
            return
        }
        val previous = singleSets.lastOrNull()
        singleSets.add(
            DiceSetDraft(
                countText = "1",
                sidesText = previous?.sidesText?.takeIf { it.isNotBlank() } ?: "6",
            ),
        )
        validationMessage = null
    }

    fun removeSingleSet(index: Int) {
        if (singleSets.size <= 1 || index !in singleSets.indices) return
        singleSets.removeAt(index)
        validationMessage = null
    }

    fun toggleSingleModifierOperation(index: Int) {
        if (index !in singleModifiers.indices) return
        singleModifiers[index] = singleModifiers[index].copy(
            operation = singleModifiers[index].operation.toggled(),
        )
        validationMessage = null
    }

    fun updateSingleModifierValue(index: Int, text: String) {
        if (index !in singleModifiers.indices) return
        singleModifiers[index] = singleModifiers[index].copy(
            valueText = text.filter { it.isDigit() }.take(6),
        )
        validationMessage = null
    }

    fun addSingleModifier() {
        if (singleModifiers.size >= MAX_SINGLE_MODIFIERS) {
            validationMessage = "A roll can contain up to $MAX_SINGLE_MODIFIERS modifiers."
            return
        }
        singleModifiers += DiceModifierDraft()
        validationMessage = null
    }

    fun removeSingleModifier(index: Int) {
        if (singleModifiers.size <= 1 || index !in singleModifiers.indices) return
        singleModifiers.removeAt(index)
        validationMessage = null
    }

    fun selectKeepMode(newMode: DiceKeepMode) {
        keepMode = newMode
        validationMessage = null
        persist()
    }

    fun rollSingle(): Boolean {
        val parsed = parseSingleControls() ?: return false
        return rollSingleSpec(parsed.sets, parsed.modifiers, keepMode)
    }

    fun quickRollSinglePreset(presetId: Long): Boolean {
        val preset = singlePresets.firstOrNull { it.id == presetId } ?: return false
        mode = DiceRollerMode.SINGLE
        validationMessage = null
        return rollSingleSpec(
            specs = preset.sets,
            modifiers = preset.modifiers,
            requestedKeepMode = preset.keepMode,
        )
    }

    fun loadSinglePreset(presetId: Long): Boolean {
        val preset = singlePresets.firstOrNull { it.id == presetId } ?: return false
        mode = DiceRollerMode.SINGLE
        singleSets.clear()
        singleSets.addAll(
            preset.sets.map { spec ->
                DiceSetDraft(
                    countText = spec.count.toString(),
                    sidesText = spec.sides.toString(),
                )
            },
        )
        singleModifiers.clear()
        singleModifiers.addAll(
            preset.modifiers.ifEmpty { listOf(DiceModifierSpec(DiceModifierOperation.ADD, 0)) }
                .map { spec ->
                    DiceModifierDraft(
                        operation = spec.operation,
                        valueText = spec.value.toString(),
                    )
                },
        )
        keepMode = preset.keepMode
        validationMessage = null
        persist()
        return true
    }

    fun saveSinglePreset(name: String, presetId: Long? = null): Long? {
        val cleanName = cleanPresetName(name) ?: return null
        val parsed = parseSingleControls() ?: return null
        val existingIndex = presetId?.let { id -> singlePresets.indexOfFirst { it.id == id } } ?: -1
        val id = if (existingIndex >= 0) {
            singlePresets[existingIndex] = singlePresets[existingIndex].copy(
                name = cleanName,
                sets = parsed.sets.map { it.copy() },
                modifiers = parsed.modifiers.map { it.copy() },
                keepMode = keepMode,
            )
            presetId!!
        } else {
            if (singlePresets.size >= MAX_PRESETS_PER_MODE) {
                validationMessage = "Single presets are limited to $MAX_PRESETS_PER_MODE."
                return null
            }
            nextPresetId++.also { newId ->
                singlePresets += SingleDicePreset(
                    id = newId,
                    name = cleanName,
                    sets = parsed.sets.map { it.copy() },
                    modifiers = parsed.modifiers.map { it.copy() },
                    keepMode = keepMode,
                )
            }
        }
        validationMessage = null
        persist()
        return id
    }

    fun deleteSinglePreset(presetId: Long): Boolean {
        val removed = singlePresets.removeAll { it.id == presetId }
        if (removed) persist()
        return removed
    }

    fun save() {
        persist()
    }

    private fun parseClusterControls(): Pair<Int, Int>? {
        val count = clusterCountText.toIntOrNull()
        val sides = clusterSidesText.toIntOrNull()
        if (count == null || count !in 1..MAX_CLUSTER_DICE) {
            validationMessage = "Cluster dice count must be between 1 and $MAX_CLUSTER_DICE."
            return null
        }
        if (sides == null || sides !in MIN_SIDES..12) {
            validationMessage = "Cluster die size must be between d$MIN_SIDES and d12."
            return null
        }
        return count to sides
    }

    private fun rollClusterSpec(count: Int, sides: Int, operationLabel: String): Boolean {
        val record = ClusterDiceRoll(
            id = nextHistoryId(),
            sides = sides,
            results = List(count) { rollDie(sides) },
            operationLabel = operationLabel,
        )
        currentClusterRoll = record
        validationMessage = null
        addHistory(record)
        return true
    }

    private fun parseSingleControls(): ParsedSingleControls? {
        val specs = parseSingleSets() ?: return null
        val modifiers = parseSingleModifiers() ?: return null
        return ParsedSingleControls(specs, modifiers)
    }

    private fun parseSingleSets(): List<DiceSetSpec>? {
        if (singleSets.isEmpty()) {
            validationMessage = "Add at least one dice set."
            return null
        }
        val parsed = mutableListOf<DiceSetSpec>()
        var totalDice = 0
        singleSets.forEachIndexed { index, draft ->
            val count = draft.countText.toIntOrNull()
            val sides = draft.sidesText.toIntOrNull()
            if (count == null || count !in 1..MAX_DICE_PER_SET) {
                validationMessage = "Set ${index + 1}: dice count must be 1–$MAX_DICE_PER_SET."
                return null
            }
            if (sides == null || sides !in MIN_SIDES..MAX_SIDES) {
                validationMessage = "Set ${index + 1}: die size must be d$MIN_SIDES–d$MAX_SIDES."
                return null
            }
            totalDice += count
            if (totalDice > MAX_SINGLE_TOTAL_DICE) {
                validationMessage = "A roll can contain at most $MAX_SINGLE_TOTAL_DICE dice total."
                return null
            }
            parsed += DiceSetSpec(count, sides)
        }
        return parsed
    }

    private fun parseSingleModifiers(): List<DiceModifierSpec>? {
        if (singleModifiers.isEmpty()) return emptyList()
        return buildList {
            singleModifiers.forEachIndexed { index, draft ->
                val value = draft.valueText.toIntOrNull()
                if (value == null || value !in 0..MAX_MODIFIER_VALUE) {
                    validationMessage = "Modifier ${index + 1}: enter a whole number from 0 to $MAX_MODIFIER_VALUE."
                    return null
                }
                add(DiceModifierSpec(draft.operation, value))
            }
        }
    }

    private fun rollSingleSpec(
        specs: List<DiceSetSpec>,
        modifiers: List<DiceModifierSpec>,
        requestedKeepMode: DiceKeepMode,
    ): Boolean {
        val first = rollAttempt(specs, modifiers)
        val second = if (requestedKeepMode == DiceKeepMode.NORMAL) {
            null
        } else {
            rollAttempt(specs, modifiers)
        }
        val keptAttempt = when (requestedKeepMode) {
            DiceKeepMode.NORMAL -> 1
            DiceKeepMode.ADVANTAGE -> if ((second?.total ?: Int.MIN_VALUE) > first.total) 2 else 1
            DiceKeepMode.DISADVANTAGE -> if ((second?.total ?: Int.MAX_VALUE) < first.total) 2 else 1
        }
        val record = SingleDiceRoll(
            id = nextHistoryId(),
            expression = formatExpression(specs, modifiers),
            keepMode = requestedKeepMode,
            first = first,
            second = second,
            keptAttempt = keptAttempt,
        )
        currentSingleRoll = record
        validationMessage = null
        addHistory(record)
        return true
    }

    private fun cleanPresetName(name: String): String? {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(MAX_PRESET_NAME_LENGTH)
        if (clean.isBlank()) {
            validationMessage = "Enter a preset name."
            return null
        }
        return clean
    }

    private fun rollAttempt(
        specs: List<DiceSetSpec>,
        modifiers: List<DiceModifierSpec>,
    ): SingleDiceAttempt =
        SingleDiceAttempt(
            sets = specs.map { spec ->
                DiceSetOutcome(
                    count = spec.count,
                    sides = spec.sides,
                    results = List(spec.count) { rollDie(spec.sides) },
                )
            },
            modifiers = modifiers.map { it.copy() },
        )

    private fun formatExpression(
        specs: List<DiceSetSpec>,
        modifiers: List<DiceModifierSpec>,
    ): String {
        val dice = specs.joinToString(" + ") { "${it.count}d${it.sides}" }
        return buildString {
            append(dice)
            modifiers.forEach { modifier ->
                append(" ${modifier.operation.symbol} ${modifier.value}")
            }
        }
    }

    private fun rollDie(sides: Int): Int = Random.Default.nextInt(from = 1, until = sides + 1)

    private fun nextHistoryId(): Long = nextHistoryId++

    private fun addHistory(entry: DiceHistoryEntry) {
        history.add(0, entry)
        while (history.size > HISTORY_LIMIT) history.removeAt(history.lastIndex)
        persist()
    }

    private fun persist() {
        val context = appContext ?: return
        val root = JSONObject().apply {
            put("version", SCHEMA_VERSION)
            put("mode", mode.name)
            put("clusterCountText", clusterCountText)
            put("clusterSidesText", clusterSidesText)
            put("keepMode", keepMode.name)
            put(
                "singleSets",
                JSONArray().apply {
                    singleSets.forEach { draft ->
                        put(
                            JSONObject().apply {
                                put("countText", draft.countText)
                                put("sidesText", draft.sidesText)
                            },
                        )
                    }
                },
            )
            put(
                "singleModifiers",
                JSONArray().apply {
                    singleModifiers.forEach { draft ->
                        put(
                            JSONObject().apply {
                                put("operation", draft.operation.name)
                                put("valueText", draft.valueText)
                            },
                        )
                    }
                },
            )
            put(
                "history",
                JSONArray().apply { history.forEach { put(encodeHistory(it)) } },
            )
            put(
                "clusterPresets",
                JSONArray().apply { clusterPresets.forEach { put(encodeClusterPreset(it)) } },
            )
            put(
                "singlePresets",
                JSONArray().apply { singlePresets.forEach { put(encodeSinglePreset(it)) } },
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, root.toString())
            .apply()
    }

    private fun load() {
        val context = appContext ?: return
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_STATE, null)
            ?: return
        runCatching {
            val root = JSONObject(raw)
            val version = root.optInt("version", 0)
            require(version in 1..SCHEMA_VERSION)
            mode = enumValueOrDefault(root.optString("mode"), DiceRollerMode.CLUSTER)
            clusterCountText = root.optString("clusterCountText", "12")
            clusterSidesText = root.optString("clusterSidesText", "6")
            keepMode = enumValueOrDefault(root.optString("keepMode"), DiceKeepMode.NORMAL)

            val loadedSets = root.optJSONArray("singleSets") ?: JSONArray()
            singleSets.clear()
            for (index in 0 until loadedSets.length()) {
                val json = loadedSets.optJSONObject(index) ?: continue
                singleSets += DiceSetDraft(
                    countText = json.optString("countText", "1"),
                    sidesText = json.optString("sidesText", "20"),
                )
            }
            if (singleSets.isEmpty()) singleSets += DiceSetDraft()

            singleModifiers.clear()
            if (version >= 3) {
                val modifierArray = root.optJSONArray("singleModifiers") ?: JSONArray()
                for (index in 0 until minOf(modifierArray.length(), MAX_SINGLE_MODIFIERS)) {
                    val json = modifierArray.optJSONObject(index) ?: continue
                    singleModifiers += DiceModifierDraft(
                        operation = enumValueOrDefault(
                            json.optString("operation"),
                            DiceModifierOperation.ADD,
                        ),
                        valueText = json.optString("valueText", "0")
                            .filter { it.isDigit() }
                            .take(6)
                            .ifBlank { "0" },
                    )
                }
            } else {
                singleModifiers += legacyModifierDraft(root.optString("singleModifierText", "0"))
            }
            if (singleModifiers.isEmpty()) singleModifiers += DiceModifierDraft()

            history.clear()
            val loadedHistory = root.optJSONArray("history") ?: JSONArray()
            for (index in 0 until minOf(loadedHistory.length(), HISTORY_LIMIT)) {
                decodeHistory(loadedHistory.optJSONObject(index))?.let(history::add)
            }

            clusterPresets.clear()
            singlePresets.clear()
            if (version >= 2) {
                val clusterArray = root.optJSONArray("clusterPresets") ?: JSONArray()
                for (index in 0 until minOf(clusterArray.length(), MAX_PRESETS_PER_MODE)) {
                    decodeClusterPreset(clusterArray.optJSONObject(index))?.let(clusterPresets::add)
                }
                val singleArray = root.optJSONArray("singlePresets") ?: JSONArray()
                for (index in 0 until minOf(singleArray.length(), MAX_PRESETS_PER_MODE)) {
                    decodeSinglePreset(singleArray.optJSONObject(index))?.let(singlePresets::add)
                }
            }

            nextHistoryId = (history.maxOfOrNull { it.id } ?: 0L) + 1L
            nextPresetId = maxOf(
                clusterPresets.maxOfOrNull { it.id } ?: 0L,
                singlePresets.maxOfOrNull { it.id } ?: 0L,
            ) + 1L
            currentClusterRoll = history.firstOrNull { it is ClusterDiceRoll } as? ClusterDiceRoll
            currentSingleRoll = history.firstOrNull { it is SingleDiceRoll } as? SingleDiceRoll
        }
    }

    private fun legacyModifierDraft(raw: String): DiceModifierDraft {
        val signed = raw.toIntOrNull() ?: 0
        return DiceModifierDraft(
            operation = if (signed < 0) {
                DiceModifierOperation.SUBTRACT
            } else {
                DiceModifierOperation.ADD
            },
            valueText = abs(signed).coerceAtMost(MAX_MODIFIER_VALUE).toString(),
        )
    }

    private fun legacyModifierSpec(value: Int): DiceModifierSpec =
        DiceModifierSpec(
            operation = if (value < 0) {
                DiceModifierOperation.SUBTRACT
            } else {
                DiceModifierOperation.ADD
            },
            value = abs(value).coerceAtMost(MAX_MODIFIER_VALUE),
        )

    private fun encodeClusterPreset(preset: ClusterDicePreset): JSONObject =
        JSONObject().apply {
            put("id", preset.id)
            put("name", preset.name)
            put("count", preset.count)
            put("sides", preset.sides)
        }

    private fun encodeSinglePreset(preset: SingleDicePreset): JSONObject =
        JSONObject().apply {
            put("id", preset.id)
            put("name", preset.name)
            put("keepMode", preset.keepMode.name)
            put(
                "sets",
                JSONArray().apply {
                    preset.sets.forEach { spec ->
                        put(
                            JSONObject().apply {
                                put("count", spec.count)
                                put("sides", spec.sides)
                            },
                        )
                    }
                },
            )
            put("modifiers", encodeModifierSpecs(preset.modifiers))
        }

    private fun decodeClusterPreset(json: JSONObject?): ClusterDicePreset? {
        json ?: return null
        val id = json.optLong("id", 0L).takeIf { it > 0L } ?: return null
        val name = json.optString("name", "").trim().take(MAX_PRESET_NAME_LENGTH)
            .takeIf { it.isNotBlank() } ?: return null
        val count = json.optInt("count", 0).takeIf { it in 1..MAX_CLUSTER_DICE } ?: return null
        val sides = json.optInt("sides", 0).takeIf { it in MIN_SIDES..12 } ?: return null
        return ClusterDicePreset(id, name, count, sides)
    }

    private fun decodeSinglePreset(json: JSONObject?): SingleDicePreset? {
        json ?: return null
        val id = json.optLong("id", 0L).takeIf { it > 0L } ?: return null
        val name = json.optString("name", "").trim().take(MAX_PRESET_NAME_LENGTH)
            .takeIf { it.isNotBlank() } ?: return null
        val keep = enumValueOrDefault(json.optString("keepMode"), DiceKeepMode.NORMAL)
        val setsJson = json.optJSONArray("sets") ?: return null
        val sets = buildList {
            for (index in 0 until minOf(setsJson.length(), MAX_SINGLE_SETS)) {
                val setJson = setsJson.optJSONObject(index) ?: continue
                val count = setJson.optInt("count", 0)
                val sides = setJson.optInt("sides", 0)
                if (count in 1..MAX_DICE_PER_SET && sides in MIN_SIDES..MAX_SIDES) {
                    add(DiceSetSpec(count, sides))
                }
            }
        }
        if (sets.isEmpty() || sets.sumOf { it.count } > MAX_SINGLE_TOTAL_DICE) return null
        val modifiers = if (json.has("modifiers")) {
            decodeModifierSpecs(json.optJSONArray("modifiers"))
        } else {
            listOf(legacyModifierSpec(json.optInt("modifier", 0)))
        }
        return SingleDicePreset(id, name, sets, modifiers, keep)
    }

    private fun encodeHistory(entry: DiceHistoryEntry): JSONObject =
        when (entry) {
            is ClusterDiceRoll -> JSONObject().apply {
                put("type", "cluster")
                put("id", entry.id)
                put("sides", entry.sides)
                put("operation", entry.operationLabel)
                put("results", JSONArray(entry.results))
            }

            is SingleDiceRoll -> JSONObject().apply {
                put("type", "single")
                put("id", entry.id)
                put("expression", entry.expression)
                put("keepMode", entry.keepMode.name)
                put("keptAttempt", entry.keptAttempt)
                put("first", encodeAttempt(entry.first))
                entry.second?.let { put("second", encodeAttempt(it)) }
            }
        }

    private fun encodeAttempt(attempt: SingleDiceAttempt): JSONObject =
        JSONObject().apply {
            put(
                "sets",
                JSONArray().apply {
                    attempt.sets.forEach { set ->
                        put(
                            JSONObject().apply {
                                put("count", set.count)
                                put("sides", set.sides)
                                put("results", JSONArray(set.results))
                            },
                        )
                    }
                },
            )
            put("modifiers", encodeModifierSpecs(attempt.modifiers))
        }

    private fun encodeModifierSpecs(modifiers: List<DiceModifierSpec>): JSONArray =
        JSONArray().apply {
            modifiers.forEach { modifier ->
                put(
                    JSONObject().apply {
                        put("operation", modifier.operation.name)
                        put("value", modifier.value)
                    },
                )
            }
        }

    private fun decodeModifierSpecs(array: JSONArray?): List<DiceModifierSpec> {
        array ?: return emptyList()
        return buildList {
            for (index in 0 until minOf(array.length(), MAX_SINGLE_MODIFIERS)) {
                val json = array.optJSONObject(index) ?: continue
                val value = json.optInt("value", -1)
                if (value !in 0..MAX_MODIFIER_VALUE) continue
                add(
                    DiceModifierSpec(
                        operation = enumValueOrDefault(
                            json.optString("operation"),
                            DiceModifierOperation.ADD,
                        ),
                        value = value,
                    ),
                )
            }
        }
    }

    private fun decodeHistory(json: JSONObject?): DiceHistoryEntry? {
        json ?: return null
        val id = json.optLong("id", 0L).takeIf { it > 0L } ?: return null
        return when (json.optString("type")) {
            "cluster" -> {
                val sides = json.optInt("sides", 0).takeIf { it in MIN_SIDES..12 } ?: return null
                val results = decodeIntArray(json.optJSONArray("results"))
                    .takeIf { values -> values.isNotEmpty() && values.all { it in 1..sides } }
                    ?: return null
                ClusterDiceRoll(
                    id = id,
                    sides = sides,
                    results = results,
                    operationLabel = json.optString("operation", "Roll"),
                )
            }

            "single" -> {
                val first = decodeAttempt(json.optJSONObject("first")) ?: return null
                val second = decodeAttempt(json.optJSONObject("second"))
                val keep = enumValueOrDefault(json.optString("keepMode"), DiceKeepMode.NORMAL)
                SingleDiceRoll(
                    id = id,
                    expression = json.optString("expression", "Dice roll"),
                    keepMode = keep,
                    first = first,
                    second = second,
                    keptAttempt = json.optInt("keptAttempt", 1).coerceIn(1, 2),
                )
            }

            else -> null
        }
    }

    private fun decodeAttempt(json: JSONObject?): SingleDiceAttempt? {
        json ?: return null
        val setsJson = json.optJSONArray("sets") ?: return null
        val sets = buildList {
            for (index in 0 until setsJson.length()) {
                val setJson = setsJson.optJSONObject(index) ?: continue
                val count = setJson.optInt("count", 0)
                val sides = setJson.optInt("sides", 0)
                val results = decodeIntArray(setJson.optJSONArray("results"))
                if (
                    count in 1..MAX_DICE_PER_SET &&
                    sides in MIN_SIDES..MAX_SIDES &&
                    results.size == count &&
                    results.all { it in 1..sides }
                ) {
                    add(DiceSetOutcome(count, sides, results))
                }
            }
        }
        if (sets.isEmpty()) return null
        val modifiers = if (json.has("modifiers")) {
            decodeModifierSpecs(json.optJSONArray("modifiers"))
        } else {
            listOf(legacyModifierSpec(json.optInt("modifier", 0)))
        }
        return SingleDiceAttempt(
            sets = sets,
            modifiers = modifiers,
        )
    }

    private fun decodeIntArray(array: JSONArray?): List<Int> {
        array ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) add(array.optInt(index))
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}
