package com.adoodguy.androidvtt.tabletop

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val BACKUP_FORMAT = "android-vtt-backup"
private const val BACKUP_VERSION = 1
private const val MANIFEST_ENTRY = "manifest.json"
private const val MAX_MANIFEST_BYTES = 10 * 1024 * 1024
private const val SCENE_PREFS_NAME = "tabletop_scene_library"
private const val SCENE_PREFS_KEY = "scene_library_json"
private const val DICE_PREFS_NAME = "dice_roller"
private const val DICE_PREFS_KEY = "state_json"
private const val DICE_SCHEMA_VERSION = 5
private const val MAX_CLUSTER_DICE = 500
private const val MAX_SINGLE_SETS = 8
private const val MAX_SINGLE_MODIFIERS = 16
private const val MAX_DICE_PER_SET = 100
private const val MAX_SINGLE_TOTAL_DICE = 500
private const val MAX_PRESETS_PER_MODE = 50
private const val MAX_PRESET_NAME_LENGTH = 40
private const val MAX_MODIFIER_VALUE = 100_000

data class AppBackupResult(
    val success: Boolean,
    val message: String,
)

private data class PreparedDiceState(
    val mode: DiceRollerMode,
    val clusterCountText: String,
    val clusterSidesText: String,
    val keepMode: DiceKeepMode,
    val singleSets: List<DiceSetDraft>,
    val singleModifiers: List<DiceModifierDraft>,
    val history: List<DiceHistoryEntry>,
    val clusterPresets: List<ClusterDicePreset>,
    val singlePresets: List<SingleDicePreset>,
)

private data class ExtractedBackup(
    val manifest: JSONObject,
    val extractedMaps: Map<String, File>,
    val tempDirectory: File,
)

object AppBackupStore {
    suspend fun exportTo(context: Context, destination: Uri): AppBackupResult {
        val appContext = context.applicationContext
        val rawState = withContext(Dispatchers.Main.immediate) {
            TabletopSceneStore.saveCurrent()
            DiceRollerStore.save()
            val sceneRaw = appContext.getSharedPreferences(SCENE_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(SCENE_PREFS_KEY, null)
            val diceRaw = appContext.getSharedPreferences(DICE_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(DICE_PREFS_KEY, null)
            sceneRaw to diceRaw
        }
        val sceneRaw = rawState.first
            ?: return AppBackupResult(false, "No scene library is available to back up.")
        val diceRaw = rawState.second
            ?: return AppBackupResult(false, "Dice state is not available to back up.")

        return withContext(Dispatchers.IO) {
            runCatching {
                val sceneRoot = JSONObject(sceneRaw)
                val diceRoot = JSONObject(diceRaw)
                val mapUris = collectMapUris(sceneRoot)
                val mapAssets = JSONArray()
                val output = appContext.contentResolver.openOutputStream(destination, "w")
                    ?: error("Could not open the selected backup destination.")

                ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                    mapUris.forEachIndexed { index, sourceUri ->
                        val entryName = "maps/map-${index + 1}.bin"
                        zip.putNextEntry(ZipEntry(entryName))
                        openBackupInput(appContext, Uri.parse(sourceUri))?.use { input ->
                            input.copyTo(zip)
                        } ?: error("Could not read a map image referenced by a saved scene.")
                        zip.closeEntry()
                        mapAssets.put(
                            JSONObject().apply {
                                put("sourceUri", sourceUri)
                                put("entry", entryName)
                            },
                        )
                    }

                    val manifest = JSONObject().apply {
                        put("format", BACKUP_FORMAT)
                        put("version", BACKUP_VERSION)
                        put("createdAtEpochMs", System.currentTimeMillis())
                        put("sceneLibrary", sceneRoot)
                        put("diceState", diceRoot)
                        put("maps", mapAssets)
                    }
                    zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                    zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }

                val sceneCount = sceneRoot.optJSONArray("scenes")?.length() ?: 0
                AppBackupResult(
                    true,
                    "Backup saved: $sceneCount scene${if (sceneCount == 1) "" else "s"}, " +
                        "${mapUris.size} map image${if (mapUris.size == 1) "" else "s"}.",
                )
            }.getOrElse { error ->
                AppBackupResult(false, error.message ?: "Backup export failed.")
            }
        }
    }

    suspend fun importFrom(context: Context, source: Uri): AppBackupResult {
        val appContext = context.applicationContext
        val previous = withContext(Dispatchers.Main.immediate) {
            TabletopSceneStore.saveCurrent()
            DiceRollerStore.save()
            val sceneRaw = appContext.getSharedPreferences(SCENE_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(SCENE_PREFS_KEY, null)
            val diceState = captureCurrentDiceState()
            sceneRaw to diceState
        }
        val previousSceneRaw = previous.first
            ?: return AppBackupResult(false, "The current scene library could not be captured safely.")
        val previousDiceState = previous.second

        return withContext(Dispatchers.IO) {
            val importedFiles = mutableListOf<File>()
            var extracted: ExtractedBackup? = null
            try {
                extracted = extractBackup(appContext, source)
                val manifest = extracted.manifest
                require(manifest.optString("format") == BACKUP_FORMAT) { "This is not an Android VTT backup." }
                require(manifest.optInt("version", 0) == BACKUP_VERSION) {
                    "Unsupported backup version: ${manifest.optInt("version", 0)}"
                }

                val sceneRoot = manifest.optJSONObject("sceneLibrary")
                    ?: error("The backup is missing its scene library.")
                val diceRoot = manifest.optJSONObject("diceState")
                    ?: error("The backup is missing its dice state.")
                val preparedDice = decodeDiceState(diceRoot)
                val referencedMapUris = collectMapUris(sceneRoot)
                val mapRecords = manifest.optJSONArray("maps") ?: JSONArray()
                val mapUriReplacements = mutableMapOf<String, String>()
                val importedMapDirectory = File(appContext.filesDir, "imported_maps").apply { mkdirs() }

                for (index in 0 until mapRecords.length()) {
                    val record = mapRecords.optJSONObject(index) ?: continue
                    val sourceUri = record.optString("sourceUri").takeIf { it.isNotBlank() }
                        ?: error("A map record is missing its source URI.")
                    val entryName = record.optString("entry").takeIf { it.isNotBlank() }
                        ?: error("A map record is missing its archive entry.")
                    val extractedFile = extracted.extractedMaps[entryName]
                        ?: error("The backup is missing map data for a saved scene.")
                    val destinationFile = File(
                        importedMapDirectory,
                        "map-${UUID.randomUUID()}.img",
                    )
                    extractedFile.copyTo(destinationFile, overwrite = false)
                    importedFiles += destinationFile
                    mapUriReplacements[sourceUri] = Uri.fromFile(destinationFile).toString()
                }

                require(referencedMapUris.all { it in mapUriReplacements }) {
                    "The backup does not contain every map image referenced by its scenes."
                }
                rewriteMapUris(sceneRoot, mapUriReplacements)

                val sceneApplied = withContext(Dispatchers.Main.immediate) {
                    TabletopSceneStore.replaceLibraryFromBackup(sceneRoot.toString())
                }
                if (!sceneApplied) error("The scene library in this backup could not be restored.")

                val diceApplied = runCatching {
                    withContext(Dispatchers.Main.immediate) {
                        applyDiceState(preparedDice, remapIds = true)
                    }
                }.isSuccess

                if (!diceApplied) {
                    withContext(Dispatchers.Main.immediate) {
                        TabletopSceneStore.replaceLibraryFromBackup(previousSceneRaw)
                        applyDiceState(previousDiceState, remapIds = false)
                    }
                    error("The dice state in this backup could not be restored.")
                }

                releaseOldMapPermissions(
                    appContext,
                    collectMapUris(JSONObject(previousSceneRaw)),
                    collectMapUris(sceneRoot),
                )
                cleanupImportedMaps(importedMapDirectory, importedFiles.toSet())

                val sceneCount = sceneRoot.optJSONArray("scenes")?.length() ?: 0
                AppBackupResult(
                    true,
                    "Backup restored: $sceneCount scene${if (sceneCount == 1) "" else "s"}, " +
                        "${importedFiles.size} map image${if (importedFiles.size == 1) "" else "s"}.",
                )
            } catch (error: Throwable) {
                importedFiles.forEach { it.delete() }
                AppBackupResult(false, error.message ?: "Backup import failed.")
            } finally {
                extracted?.tempDirectory?.deleteRecursively()
            }
        }
    }

    private fun extractBackup(context: Context, source: Uri): ExtractedBackup {
        val tempDirectory = File(context.cacheDir, "backup-import-${UUID.randomUUID()}").apply {
            require(mkdirs() || isDirectory) { "Could not prepare temporary backup storage." }
        }
        val extractedMaps = mutableMapOf<String, File>()
        var manifest: JSONObject? = null
        val input = context.contentResolver.openInputStream(source)
            ?: error("Could not open the selected backup file.")

        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.isDirectory -> Unit
                    entry.name == MANIFEST_ENTRY -> {
                        require(manifest == null) { "The backup contains multiple manifests." }
                        manifest = JSONObject(readLimitedEntry(zip).toString(Charsets.UTF_8))
                    }
                    entry.name.startsWith("maps/") -> {
                        require(entry.name !in extractedMaps) { "The backup contains duplicate map entries." }
                        val tempFile = File(tempDirectory, "map-${UUID.randomUUID()}.tmp")
                        tempFile.outputStream().buffered().use { output -> zip.copyTo(output) }
                        extractedMaps[entry.name] = tempFile
                    }
                    else -> drainEntry(zip)
                }
                zip.closeEntry()
            }
        }

        return ExtractedBackup(
            manifest = manifest ?: error("The backup manifest is missing."),
            extractedMaps = extractedMaps,
            tempDirectory = tempDirectory,
        )
    }

    private fun readLimitedEntry(zip: ZipInputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = zip.read(buffer)
            if (read <= 0) break
            total += read
            require(total <= MAX_MANIFEST_BYTES) { "The backup manifest is unexpectedly large." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun drainEntry(zip: ZipInputStream) {
        val buffer = ByteArray(8 * 1024)
        while (zip.read(buffer) > 0) Unit
    }

    private fun collectMapUris(sceneRoot: JSONObject): LinkedHashSet<String> {
        val result = linkedSetOf<String>()
        val scenes = sceneRoot.optJSONArray("scenes") ?: return result
        for (index in 0 until scenes.length()) {
            val map = scenes.optJSONObject(index)?.optJSONObject("map") ?: continue
            val uri = map.optString("imageUri", "")
                .takeIf { it.isNotBlank() && it != "null" }
                ?: continue
            result += uri
        }
        return result
    }

    private fun rewriteMapUris(sceneRoot: JSONObject, replacements: Map<String, String>) {
        val scenes = sceneRoot.optJSONArray("scenes") ?: return
        for (index in 0 until scenes.length()) {
            val map = scenes.optJSONObject(index)?.optJSONObject("map") ?: continue
            val oldUri = map.optString("imageUri", "")
                .takeIf { it.isNotBlank() && it != "null" }
                ?: continue
            map.put("imageUri", replacements[oldUri] ?: error("A map image replacement is missing."))
        }
    }

    private fun openBackupInput(context: Context, uri: Uri) =
        if (uri.scheme == "file") {
            uri.path?.let { FileInputStream(File(it)) }
        } else {
            context.contentResolver.openInputStream(uri)
        }

    private fun releaseOldMapPermissions(
        context: Context,
        previousUris: Set<String>,
        restoredUris: Set<String>,
    ) {
        (previousUris - restoredUris).forEach { rawUri ->
            val uri = Uri.parse(rawUri)
            if (uri.scheme != "content") return@forEach
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // The source provider did not grant a persistable permission.
            }
        }
    }

    private fun cleanupImportedMaps(directory: File, keep: Set<File>) {
        directory.listFiles()?.forEach { file ->
            if (file !in keep) file.delete()
        }
    }

    private fun captureCurrentDiceState(): PreparedDiceState = PreparedDiceState(
        mode = DiceRollerStore.mode,
        clusterCountText = DiceRollerStore.clusterCountText,
        clusterSidesText = DiceRollerStore.clusterSidesText,
        keepMode = DiceRollerStore.keepMode,
        singleSets = DiceRollerStore.singleSets.map { it.copy() },
        singleModifiers = DiceRollerStore.singleModifiers.map { it.copy() },
        history = DiceRollerStore.history.map(::copyHistoryEntry),
        clusterPresets = DiceRollerStore.clusterPresets.map { it.copy() },
        singlePresets = DiceRollerStore.singlePresets.map { preset ->
            preset.copy(
                sets = preset.sets.map { it.copy() },
                modifiers = preset.modifiers.map { it.copy() },
            )
        },
    )

    private fun applyDiceState(state: PreparedDiceState, remapIds: Boolean) {
        val applied = if (remapIds) remapDiceIds(state) else state
        DiceRollerStore.closePanel()
        DiceRollerStore.selectMode(applied.mode)
        DiceRollerStore.updateClusterCount(applied.clusterCountText)
        DiceRollerStore.updateClusterSides(applied.clusterSidesText)
        DiceRollerStore.singleSets.clear()
        DiceRollerStore.singleSets.addAll(applied.singleSets.map { it.copy() })
        DiceRollerStore.singleModifiers.clear()
        DiceRollerStore.singleModifiers.addAll(applied.singleModifiers.map { it.copy() })
        DiceRollerStore.selectKeepMode(applied.keepMode)
        DiceRollerStore.history.clear()
        DiceRollerStore.history.addAll(applied.history.map(::copyHistoryEntry))
        DiceRollerStore.clusterPresets.clear()
        DiceRollerStore.clusterPresets.addAll(applied.clusterPresets.map { it.copy() })
        DiceRollerStore.singlePresets.clear()
        DiceRollerStore.singlePresets.addAll(
            applied.singlePresets.map { preset ->
                preset.copy(
                    sets = preset.sets.map { it.copy() },
                    modifiers = preset.modifiers.map { it.copy() },
                )
            },
        )
        DiceRollerStore.save()
    }

    private fun remapDiceIds(state: PreparedDiceState): PreparedDiceState {
        val base = maxOf(System.currentTimeMillis() * 1_000L, 1_000_000_000_000L)
        val history = state.history.mapIndexed { index, entry ->
            when (entry) {
                is ClusterDiceRoll -> entry.copy(id = base + index)
                is SingleDiceRoll -> entry.copy(id = base + index)
            }
        }
        val presetBase = base + 10_000L
        return state.copy(
            history = history,
            clusterPresets = state.clusterPresets.mapIndexed { index, preset ->
                preset.copy(id = presetBase + index)
            },
            singlePresets = state.singlePresets.mapIndexed { index, preset ->
                preset.copy(id = presetBase + 1_000L + index)
            },
        )
    }

    private fun copyHistoryEntry(entry: DiceHistoryEntry): DiceHistoryEntry = when (entry) {
        is ClusterDiceRoll -> entry.copy(results = entry.results.toList())
        is SingleDiceRoll -> entry.copy(
            first = copyAttempt(entry.first),
            second = entry.second?.let(::copyAttempt),
        )
    }

    private fun copyAttempt(attempt: SingleDiceAttempt): SingleDiceAttempt = attempt.copy(
        sets = attempt.sets.map { set -> set.copy(results = set.results.toList()) },
        modifiers = attempt.modifiers.map { it.copy() },
    )

    private fun decodeDiceState(root: JSONObject): PreparedDiceState {
        require(root.optInt("version", 0) == DICE_SCHEMA_VERSION) {
            "Unsupported dice-state version: ${root.optInt("version", 0)}"
        }
        val mode = enumValueOrDefault(root.optString("mode"), DiceRollerMode.CLUSTER)
        val keepMode = enumValueOrDefault(root.optString("keepMode"), DiceKeepMode.NORMAL)
        val clusterCountText = root.optString("clusterCountText", "12")
            .filter { it.isDigit() }.take(4).ifBlank { "12" }
        val clusterSidesText = root.optString("clusterSidesText", "6")
            .filter { it.isDigit() }.take(3).ifBlank { "6" }

        val setDrafts = decodeSetDrafts(root.optJSONArray("singleSets"))
            .ifEmpty { listOf(DiceSetDraft()) }
        val modifierDrafts = decodeModifierDrafts(root.optJSONArray("singleModifiers"))
            .ifEmpty { listOf(DiceModifierDraft()) }
        val history = decodeHistory(root.optJSONArray("history"))
        val clusterPresets = decodeClusterPresets(root.optJSONArray("clusterPresets"))
        val singlePresets = decodeSinglePresets(root.optJSONArray("singlePresets"))

        return PreparedDiceState(
            mode = mode,
            clusterCountText = clusterCountText,
            clusterSidesText = clusterSidesText,
            keepMode = keepMode,
            singleSets = setDrafts,
            singleModifiers = modifierDrafts,
            history = history,
            clusterPresets = clusterPresets,
            singlePresets = singlePresets,
        )
    }

    private fun decodeSetDrafts(array: JSONArray?): List<DiceSetDraft> {
        array ?: return emptyList()
        return buildList {
            var totalDice = 0
            for (index in 0 until minOf(array.length(), MAX_SINGLE_SETS)) {
                val json = array.optJSONObject(index) ?: continue
                val countText = json.optString("countText", "1").filter { it.isDigit() }.take(3)
                val sidesText = json.optString("sidesText", "20").filter { it.isDigit() }.take(3)
                val count = countText.toIntOrNull() ?: continue
                val sides = sidesText.toIntOrNull() ?: continue
                if (count !in 1..MAX_DICE_PER_SET || sides !in 2..100) continue
                totalDice += count
                if (totalDice > MAX_SINGLE_TOTAL_DICE) break
                add(
                    DiceSetDraft(
                        operation = enumValueOrDefault(
                            json.optString("operation"),
                            DiceModifierOperation.ADD,
                        ),
                        countText = count.toString(),
                        sidesText = sides.toString(),
                    ),
                )
            }
        }
    }

    private fun decodeModifierDrafts(array: JSONArray?): List<DiceModifierDraft> {
        array ?: return emptyList()
        return buildList {
            for (index in 0 until minOf(array.length(), MAX_SINGLE_MODIFIERS)) {
                val json = array.optJSONObject(index) ?: continue
                val value = json.optString("valueText", "0").filter { it.isDigit() }.take(6)
                    .toIntOrNull() ?: continue
                if (value !in 0..MAX_MODIFIER_VALUE) continue
                add(
                    DiceModifierDraft(
                        operation = enumValueOrDefault(
                            json.optString("operation"),
                            DiceModifierOperation.ADD,
                        ),
                        valueText = value.toString(),
                    ),
                )
            }
        }
    }

    private fun decodeHistory(array: JSONArray?): List<DiceHistoryEntry> {
        array ?: return emptyList()
        return buildList {
            for (index in 0 until minOf(array.length(), 5)) {
                val json = array.optJSONObject(index) ?: continue
                val id = (index + 1).toLong()
                when (json.optString("type")) {
                    "cluster" -> decodeClusterHistory(json, id)?.let(::add)
                    "single" -> decodeSingleHistory(json, id)?.let(::add)
                }
            }
        }
    }

    private fun decodeClusterHistory(json: JSONObject, id: Long): ClusterDiceRoll? {
        val sides = json.optInt("sides", 0).takeIf { it in 2..12 } ?: return null
        val resultsJson = json.optJSONArray("results") ?: return null
        if (resultsJson.length() !in 1..MAX_CLUSTER_DICE) return null
        val results = buildList {
            for (index in 0 until resultsJson.length()) {
                val value = resultsJson.optInt(index, 0)
                if (value !in 1..sides) return null
                add(value)
            }
        }
        return ClusterDiceRoll(
            id = id,
            sides = sides,
            results = results,
            operationLabel = json.optString("operation", "Roll"),
        )
    }

    private fun decodeSingleHistory(json: JSONObject, id: Long): SingleDiceRoll? {
        val first = decodeAttempt(json.optJSONObject("first")) ?: return null
        val second = decodeAttempt(json.optJSONObject("second"))
        val keepMode = enumValueOrDefault(json.optString("keepMode"), DiceKeepMode.NORMAL)
        if (keepMode != DiceKeepMode.NORMAL && second == null) return null
        return SingleDiceRoll(
            id = id,
            expression = json.optString("expression", "Dice roll").take(500),
            keepMode = keepMode,
            first = first,
            second = second,
            keptAttempt = json.optInt("keptAttempt", 1).coerceIn(1, 2),
        )
    }

    private fun decodeAttempt(json: JSONObject?): SingleDiceAttempt? {
        json ?: return null
        val setsJson = json.optJSONArray("sets") ?: return null
        val sets = buildList {
            var totalDice = 0
            for (index in 0 until minOf(setsJson.length(), MAX_SINGLE_SETS)) {
                val setJson = setsJson.optJSONObject(index) ?: continue
                val count = setJson.optInt("count", 0)
                val sides = setJson.optInt("sides", 0)
                if (count !in 1..MAX_DICE_PER_SET || sides !in 2..100) return null
                totalDice += count
                if (totalDice > MAX_SINGLE_TOTAL_DICE) return null
                val resultsJson = setJson.optJSONArray("results") ?: return null
                if (resultsJson.length() != count) return null
                val results = buildList {
                    for (resultIndex in 0 until resultsJson.length()) {
                        val value = resultsJson.optInt(resultIndex, 0)
                        if (value !in 1..sides) return null
                        add(value)
                    }
                }
                add(
                    DiceSetOutcome(
                        operation = enumValueOrDefault(
                            setJson.optString("operation"),
                            DiceModifierOperation.ADD,
                        ),
                        count = count,
                        sides = sides,
                        results = results,
                    ),
                )
            }
        }
        if (sets.isEmpty()) return null
        return SingleDiceAttempt(
            sets = sets,
            modifiers = decodeModifierSpecs(json.optJSONArray("modifiers")),
        )
    }

    private fun decodeClusterPresets(array: JSONArray?): List<ClusterDicePreset> {
        array ?: return emptyList()
        return buildList {
            for (index in 0 until minOf(array.length(), MAX_PRESETS_PER_MODE)) {
                val json = array.optJSONObject(index) ?: continue
                val name = json.optString("name").trim().take(MAX_PRESET_NAME_LENGTH)
                val count = json.optInt("count", 0)
                val sides = json.optInt("sides", 0)
                if (name.isBlank() || count !in 1..MAX_CLUSTER_DICE || sides !in 2..12) continue
                add(ClusterDicePreset(index + 1L, name, count, sides))
            }
        }
    }

    private fun decodeSinglePresets(array: JSONArray?): List<SingleDicePreset> {
        array ?: return emptyList()
        return buildList {
            for (index in 0 until minOf(array.length(), MAX_PRESETS_PER_MODE)) {
                val json = array.optJSONObject(index) ?: continue
                val name = json.optString("name").trim().take(MAX_PRESET_NAME_LENGTH)
                if (name.isBlank()) continue
                val sets = decodeSetSpecs(json.optJSONArray("sets"))
                if (sets.isEmpty()) continue
                add(
                    SingleDicePreset(
                        id = index + 1L,
                        name = name,
                        sets = sets,
                        modifiers = decodeModifierSpecs(json.optJSONArray("modifiers")),
                        keepMode = enumValueOrDefault(
                            json.optString("keepMode"),
                            DiceKeepMode.NORMAL,
                        ),
                    ),
                )
            }
        }
    }

    private fun decodeSetSpecs(array: JSONArray?): List<DiceSetSpec> {
        array ?: return emptyList()
        return buildList {
            var totalDice = 0
            for (index in 0 until minOf(array.length(), MAX_SINGLE_SETS)) {
                val json = array.optJSONObject(index) ?: continue
                val count = json.optInt("count", 0)
                val sides = json.optInt("sides", 0)
                if (count !in 1..MAX_DICE_PER_SET || sides !in 2..100) continue
                totalDice += count
                if (totalDice > MAX_SINGLE_TOTAL_DICE) break
                add(
                    DiceSetSpec(
                        operation = enumValueOrDefault(
                            json.optString("operation"),
                            DiceModifierOperation.ADD,
                        ),
                        count = count,
                        sides = sides,
                    ),
                )
            }
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

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}
