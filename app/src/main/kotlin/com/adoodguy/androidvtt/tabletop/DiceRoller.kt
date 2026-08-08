package com.adoodguy.androidvtt.tabletop

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

data class DiceSetDraft(
    val countText: String = "1",
    val sidesText: String = "20",
)

data class DiceSetSpec(
    val count: Int,
    val sides: Int,
)

data class DiceSetOutcome(
    val count: Int,
    val sides: Int,
    val results: List<Int>,
) {
    val subtotal: Int get() = results.sum()
}

data class SingleDiceAttempt(
    val sets: List<DiceSetOutcome>,
    val modifier: Int,
) {
    val total: Int get() = sets.sumOf { it.subtotal } + modifier
}

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

/**
 * App-wide dice utility state. Dice history is intentionally independent of tabletop scenes.
 */
object DiceRollerStore {
    private const val PREFS_NAME = "dice_roller"
    private const val KEY_STATE = "state_json"
    private const val SCHEMA_VERSION = 1
    private const val HISTORY_LIMIT = 5
    private const val MAX_CLUSTER_DICE = 500
    private const val MAX_SINGLE_SETS = 8
    private const val MAX_DICE_PER_SET = 100
    private const val MAX_SINGLE_TOTAL_DICE = 500
    private const val MIN_SIDES = 2
    private const val MAX_SIDES = 100
    private const val MAX_ABSOLUTE_MODIFIER = 100_000

    private var appContext: Context? = null
    private var nextHistoryId = 1L

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

    var singleModifierText by mutableStateOf("0")
        private set

    var keepMode by mutableStateOf(DiceKeepMode.NORMAL)
        private set

    var currentSingleRoll by mutableStateOf<SingleDiceRoll?>(null)
        private set

    var validationMessage by mutableStateOf<String?>(null)
        private set

    val history = mutableStateListOf<DiceHistoryEntry>()

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
        val count = clusterCountText.toIntOrNull()
        val sides = clusterSidesText.toIntOrNull()
        if (count == null || count !in 1..MAX_CLUSTER_DICE) {
            validationMessage = "Cluster dice count must be between 1 and $MAX_CLUSTER_DICE."
            return false
        }
        if (sides == null || sides !in MIN_SIDES..12) {
            validationMessage = "Cluster die size must be between d$MIN_SIDES and d12."
            return false
        }

        val record = ClusterDiceRoll(
            id = nextId(),
            sides = sides,
            results = List(count) { rollDie(sides) },
        )
        currentClusterRoll = record
        validationMessage = null
        addHistory(record)
        return true
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
            id = nextId(),
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

    fun updateSingleModifier(text: String) {
        val trimmed = text.trim()
        val allowed = buildString {
            trimmed.forEachIndexed { index, char ->
                if (char.isDigit() || (index == 0 && (char == '-' || char == '+'))) append(char)
            }
        }.take(7)
        singleModifierText = allowed
        validationMessage = null
    }

    fun selectKeepMode(newMode: DiceKeepMode) {
        keepMode = newMode
        validationMessage = null
        persist()
    }

    fun rollSingle(): Boolean {
        val specs = parseSingleSets() ?: return false
        val modifier = singleModifierText.toIntOrNull() ?: run {
            validationMessage = "Modifier must be a whole number, such as 3 or -2."
            return false
        }
        if (modifier !in -MAX_ABSOLUTE_MODIFIER..MAX_ABSOLUTE_MODIFIER) {
            validationMessage = "Modifier is too large."
            return false
        }

        val first = rollAttempt(specs, modifier)
        val second = if (keepMode == DiceKeepMode.NORMAL) null else rollAttempt(specs, modifier)
        val keptAttempt = when (keepMode) {
            DiceKeepMode.NORMAL -> 1
            DiceKeepMode.ADVANTAGE -> if ((second?.total ?: Int.MIN_VALUE) > first.total) 2 else 1
            DiceKeepMode.DISADVANTAGE -> if ((second?.total ?: Int.MAX_VALUE) < first.total) 2 else 1
        }
        val record = SingleDiceRoll(
            id = nextId(),
            expression = formatExpression(specs, modifier),
            keepMode = keepMode,
            first = first,
            second = second,
            keptAttempt = keptAttempt,
        )
        currentSingleRoll = record
        validationMessage = null
        addHistory(record)
        return true
    }

    fun save() {
        persist()
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

    private fun rollAttempt(specs: List<DiceSetSpec>, modifier: Int): SingleDiceAttempt =
        SingleDiceAttempt(
            sets = specs.map { spec ->
                DiceSetOutcome(
                    count = spec.count,
                    sides = spec.sides,
                    results = List(spec.count) { rollDie(spec.sides) },
                )
            },
            modifier = modifier,
        )

    private fun formatExpression(specs: List<DiceSetSpec>, modifier: Int): String {
        val dice = specs.joinToString(" + ") { "${it.count}d${it.sides}" }
        return when {
            modifier > 0 -> "$dice + $modifier"
            modifier < 0 -> "$dice - ${-modifier}"
            else -> dice
        }
    }

    private fun rollDie(sides: Int): Int = Random.Default.nextInt(from = 1, until = sides + 1)

    private fun nextId(): Long = nextHistoryId++

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
            put("singleModifierText", singleModifierText)
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
                "history",
                JSONArray().apply { history.forEach { put(encodeHistory(it)) } },
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
            require(root.optInt("version", 0) in 1..SCHEMA_VERSION)
            mode = enumValueOrDefault(root.optString("mode"), DiceRollerMode.CLUSTER)
            clusterCountText = root.optString("clusterCountText", "12")
            clusterSidesText = root.optString("clusterSidesText", "6")
            singleModifierText = root.optString("singleModifierText", "0")
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

            history.clear()
            val loadedHistory = root.optJSONArray("history") ?: JSONArray()
            for (index in 0 until minOf(loadedHistory.length(), HISTORY_LIMIT)) {
                decodeHistory(loadedHistory.optJSONObject(index))?.let(history::add)
            }
            nextHistoryId = (history.maxOfOrNull { it.id } ?: 0L) + 1L
            currentClusterRoll = history.firstOrNull { it is ClusterDiceRoll } as? ClusterDiceRoll
            currentSingleRoll = history.firstOrNull { it is SingleDiceRoll } as? SingleDiceRoll
        }
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
            put("modifier", attempt.modifier)
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
        return SingleDiceAttempt(
            sets = sets,
            modifier = json.optInt("modifier", 0),
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
