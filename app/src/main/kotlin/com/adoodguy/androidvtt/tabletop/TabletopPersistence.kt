package com.adoodguy.androidvtt.tabletop

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.adoodguy.androidvtt.geometry.GridKind
import com.adoodguy.androidvtt.geometry.HexOrientation
import com.adoodguy.androidvtt.geometry.WorldPoint
import org.json.JSONArray
import org.json.JSONObject

internal data class TabletopSceneSnapshot(
    val gridKind: GridKind,
    val hexOrientation: HexOrientation,
    val snapEnabled: Boolean,
    val cameraCenter: WorldPoint,
    val pixelsPerWorldUnit: Double,
    val displayedUnitsPerCell: Double,
    val tokens: List<TabletopToken>,
    val measurement: MeasurementPath?,
    val strokes: List<DrawingStroke>,
    val brushColorArgb: Long,
    val notes: List<TabletopNote>,
)

data class TabletopSceneSummary(
    val id: Long,
    val name: String,
)

private data class TabletopSceneRecord(
    val id: Long,
    val name: String,
    val tabletop: TabletopSceneSnapshot,
    val map: TabletopMapConfiguration,
)

private data class DecodedSceneLibrary(
    val activeSceneId: Long,
    val nextSceneId: Long,
    val scenes: List<TabletopSceneRecord>,
)

/**
 * Owns the named scene library and autosaves the currently active scene.
 *
 * Scene-library schema 1 wraps the existing tabletop schema 2 payload together with
 * a complete map configuration. If no library exists yet, the old single autosave
 * and map preferences are imported into Scene 1 without deleting the legacy data.
 */
object TabletopSceneStore {
    private const val LIBRARY_PREFS_NAME = "tabletop_scene_library"
    private const val KEY_LIBRARY = "scene_library_json"
    private const val LIBRARY_SCHEMA_VERSION = 1

    private const val LEGACY_PREFS_NAME = "tabletop_scene"
    private const val LEGACY_KEY_AUTOSAVE = "autosave_json"
    private const val TABLETOP_SCHEMA_VERSION = 2
    private const val MAX_SCENE_NAME_LENGTH = 60

    private var appContext: Context? = null
    private var attachedState: TabletopState? = null
    private var nextSceneId = 1L

    private val records = mutableStateListOf<TabletopSceneRecord>()

    var activeSceneId by mutableLongStateOf(0L)
        private set

    val scenes: List<TabletopSceneSummary>
        get() = records.map { TabletopSceneSummary(it.id, it.name) }

    val activeSceneName: String
        get() = records.firstOrNull { it.id == activeSceneId }?.name ?: "Scene"

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        loadLibrary()?.let(::installLibrary)
    }

    fun attachAndRestore(state: TabletopState) {
        attachedState = state
        if (records.isEmpty()) {
            migrateLegacyOrCreateInitialScene(state)
        }
        restoreActiveScene()
    }

    fun saveCurrent() {
        val state = attachedState ?: return
        val index = records.indexOfFirst { it.id == activeSceneId }
        if (index < 0) return
        val current = records[index]
        records[index] = current.copy(
            tabletop = deepCopySnapshot(state.createPersistentSnapshot()),
            map = TabletopMapStore.configuration,
        )
        persistLibrary()
    }

    fun switchTo(sceneId: Long): Boolean {
        if (sceneId == activeSceneId) return records.any { it.id == sceneId }
        if (records.none { it.id == sceneId }) return false
        saveCurrent()
        activeSceneId = sceneId
        restoreActiveScene()
        persistLibrary()
        return true
    }

    fun createScene(): Long {
        saveCurrent()
        val id = nextSceneId++
        val record = TabletopSceneRecord(
            id = id,
            name = nextDefaultSceneName(),
            tabletop = blankSnapshot(),
            map = TabletopMapConfiguration(),
        )
        records.add(record)
        activeSceneId = id
        restoreActiveScene()
        persistLibrary()
        return id
    }

    fun duplicateActiveScene(): Long? {
        saveCurrent()
        val source = records.firstOrNull { it.id == activeSceneId } ?: return null
        val id = nextSceneId++
        val duplicate = source.copy(
            id = id,
            name = uniqueCopyName(source.name),
            tabletop = deepCopySnapshot(source.tabletop),
        )
        records.add(duplicate)
        activeSceneId = id
        restoreActiveScene()
        persistLibrary()
        return id
    }

    fun renameActiveScene(name: String): Boolean {
        val normalized = name.trim().take(MAX_SCENE_NAME_LENGTH)
        if (normalized.isBlank()) return false
        val index = records.indexOfFirst { it.id == activeSceneId }
        if (index < 0) return false
        records[index] = records[index].copy(name = normalized)
        persistLibrary()
        return true
    }

    fun deleteActiveScene(): Boolean {
        if (records.size <= 1) return false
        val index = records.indexOfFirst { it.id == activeSceneId }
        if (index < 0) return false
        records.removeAt(index)
        val replacementIndex = index.coerceAtMost(records.lastIndex)
        activeSceneId = records[replacementIndex].id
        restoreActiveScene()
        persistLibrary()
        return true
    }

    fun isMapUriReferencedByOtherScene(uri: String): Boolean =
        records.any { record ->
            record.id != activeSceneId && record.map.imageUri == uri
        }

    private fun restoreActiveScene() {
        val state = attachedState ?: return
        val record = records.firstOrNull { it.id == activeSceneId } ?: return
        state.restorePersistentSnapshot(deepCopySnapshot(record.tabletop))
        TabletopMapStore.restoreSceneConfiguration(record.map)
        WorkspaceModeStore.select(TabletopMode.TOKENS, state)
    }

    private fun migrateLegacyOrCreateInitialScene(state: TabletopState) {
        val legacy = loadLegacyAutosave() ?: state.createPersistentSnapshot()
        val first = TabletopSceneRecord(
            id = 1L,
            name = "Scene 1",
            tabletop = deepCopySnapshot(legacy),
            map = TabletopMapStore.configuration,
        )
        records.clear()
        records.add(first)
        activeSceneId = first.id
        nextSceneId = 2L
        persistLibrary()
    }

    private fun nextDefaultSceneName(): String {
        var number = 1
        while (records.any { it.name.equals("Scene $number", ignoreCase = true) }) {
            number += 1
        }
        return "Scene $number"
    }

    private fun uniqueCopyName(sourceName: String): String {
        val base = "$sourceName copy".take(MAX_SCENE_NAME_LENGTH)
        if (records.none { it.name.equals(base, ignoreCase = true) }) return base
        var number = 2
        while (true) {
            val suffix = " $number"
            val candidate = base.take(MAX_SCENE_NAME_LENGTH - suffix.length) + suffix
            if (records.none { it.name.equals(candidate, ignoreCase = true) }) return candidate
            number += 1
        }
    }

    private fun blankSnapshot(): TabletopSceneSnapshot =
        TabletopSceneSnapshot(
            gridKind = GridKind.HEX,
            hexOrientation = HexOrientation.POINTY_TOP,
            snapEnabled = true,
            cameraCenter = WorldPoint.Zero,
            pixelsPerWorldUnit = 96.0,
            displayedUnitsPerCell = 5.0,
            tokens = emptyList(),
            measurement = null,
            strokes = emptyList(),
            brushColorArgb = DEFAULT_DRAWING_COLOR_ARGB,
            notes = emptyList(),
        )

    private fun deepCopySnapshot(snapshot: TabletopSceneSnapshot): TabletopSceneSnapshot =
        snapshot.copy(
            tokens = snapshot.tokens.toList(),
            measurement = snapshot.measurement?.let { MeasurementPath(it.points.toList()) },
            strokes = snapshot.strokes.map { stroke ->
                stroke.copy(points = stroke.points.toList())
            },
            notes = snapshot.notes.toList(),
        )

    private fun installLibrary(library: DecodedSceneLibrary) {
        if (library.scenes.isEmpty()) return
        records.clear()
        records.addAll(library.scenes)
        activeSceneId = library.activeSceneId
            .takeIf { id -> records.any { it.id == id } }
            ?: records.first().id
        nextSceneId = maxOf(
            library.nextSceneId,
            (records.maxOfOrNull { it.id } ?: 0L) + 1L,
        )
    }

    private fun persistLibrary() {
        val context = appContext ?: return
        if (records.isEmpty()) return
        val encoded = JSONObject().apply {
            put("version", LIBRARY_SCHEMA_VERSION)
            put("activeSceneId", activeSceneId)
            put("nextSceneId", nextSceneId)
            put(
                "scenes",
                JSONArray().apply {
                    records.forEach { record -> put(encodeSceneRecord(record)) }
                },
            )
        }
        context.getSharedPreferences(LIBRARY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIBRARY, encoded.toString())
            .apply()
    }

    private fun loadLibrary(): DecodedSceneLibrary? {
        val context = appContext ?: return null
        val raw = context.getSharedPreferences(LIBRARY_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LIBRARY, null)
            ?: return null
        return runCatching { decodeLibrary(JSONObject(raw)) }.getOrNull()
    }

    private fun loadLegacyAutosave(): TabletopSceneSnapshot? {
        val context = appContext ?: return null
        val raw = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(LEGACY_KEY_AUTOSAVE, null)
            ?: return null
        return runCatching { decodeTabletop(JSONObject(raw)) }.getOrNull()
    }

    private fun encodeSceneRecord(record: TabletopSceneRecord): JSONObject =
        JSONObject().apply {
            put("id", record.id)
            put("name", record.name)
            put("tabletop", encodeTabletop(record.tabletop))
            put("map", encodeMap(record.map))
        }

    private fun decodeLibrary(root: JSONObject): DecodedSceneLibrary {
        val version = root.optInt("version", 0)
        require(version == LIBRARY_SCHEMA_VERSION) { "Unsupported scene library schema: $version" }
        val scenesJson = root.optJSONArray("scenes") ?: JSONArray()
        val decoded = buildList {
            for (index in 0 until scenesJson.length()) {
                decodeSceneRecord(scenesJson.optJSONObject(index))?.let(::add)
            }
        }
        require(decoded.isNotEmpty()) { "Scene library is empty" }
        return DecodedSceneLibrary(
            activeSceneId = root.optLong("activeSceneId", decoded.first().id),
            nextSceneId = root.optLong("nextSceneId", (decoded.maxOfOrNull { it.id } ?: 0L) + 1L),
            scenes = decoded,
        )
    }

    private fun decodeSceneRecord(json: JSONObject?): TabletopSceneRecord? {
        json ?: return null
        val id = json.optLong("id", 0L).takeIf { it > 0L } ?: return null
        val name = json.optString("name", "Scene").trim().take(MAX_SCENE_NAME_LENGTH)
            .ifBlank { "Scene $id" }
        val tabletop = json.optJSONObject("tabletop")?.let(::decodeTabletop) ?: return null
        val map = json.optJSONObject("map")?.let(::decodeMap) ?: TabletopMapConfiguration()
        return TabletopSceneRecord(id, name, deepCopySnapshot(tabletop), map)
    }

    private fun encodeMap(map: TabletopMapConfiguration): JSONObject =
        JSONObject().apply {
            put("imageUri", map.imageUri ?: JSONObject.NULL)
            put("widthCells", map.widthCells)
            put("heightCells", map.heightCells)
            put("centerX", map.centerX)
            put("centerY", map.centerY)
            put("rotationDegrees", map.rotationDegrees)
            put("snapAnchorU", map.snapAnchorU)
            put("snapAnchorV", map.snapAnchorV)
            put("movementLocked", map.movementLocked)
            put("scaleLocked", map.scaleLocked)
            put("rotationLocked", map.rotationLocked)
        }

    private fun decodeMap(json: JSONObject): TabletopMapConfiguration {
        val width = finiteOrDefault(json.optDouble("widthCells"), 24.0)
            .takeIf { it in 0.1..100_000.0 } ?: 24.0
        val height = finiteOrDefault(json.optDouble("heightCells"), 24.0)
            .takeIf { it in 0.1..100_000.0 } ?: 24.0
        val anchorU = finiteOrDefault(json.optDouble("snapAnchorU"), 0.0)
            .takeIf { it in -0.5..0.5 } ?: 0.0
        val anchorV = finiteOrDefault(json.optDouble("snapAnchorV"), 0.0)
            .takeIf { it in -0.5..0.5 } ?: 0.0
        return TabletopMapConfiguration(
            imageUri = json.optString("imageUri", "").takeIf { it.isNotBlank() && it != "null" },
            widthCells = width,
            heightCells = height,
            centerX = finiteOrDefault(json.optDouble("centerX"), 0.0),
            centerY = finiteOrDefault(json.optDouble("centerY"), 0.0),
            rotationDegrees = normalizeDegrees(
                finiteOrDefault(json.optDouble("rotationDegrees"), 0.0),
            ),
            snapAnchorU = anchorU,
            snapAnchorV = anchorV,
            movementLocked = json.optBoolean("movementLocked", false),
            scaleLocked = json.optBoolean("scaleLocked", false),
            rotationLocked = json.optBoolean("rotationLocked", false),
        )
    }

    private fun encodeTabletop(snapshot: TabletopSceneSnapshot): JSONObject =
        JSONObject().apply {
            put("version", TABLETOP_SCHEMA_VERSION)
            put("gridKind", snapshot.gridKind.name)
            put("hexOrientation", snapshot.hexOrientation.name)
            put("snapEnabled", snapshot.snapEnabled)
            put("cameraX", snapshot.cameraCenter.x)
            put("cameraY", snapshot.cameraCenter.y)
            put("pixelsPerWorldUnit", snapshot.pixelsPerWorldUnit)
            put("displayedUnitsPerCell", snapshot.displayedUnitsPerCell)
            put("brushColorArgb", snapshot.brushColorArgb)
            put(
                "tokens",
                JSONArray().apply {
                    snapshot.tokens.forEach { token -> put(encodeToken(token)) }
                },
            )
            put(
                "measurement",
                snapshot.measurement?.let(::encodeMeasurement) ?: JSONObject.NULL,
            )
            put(
                "strokes",
                JSONArray().apply {
                    snapshot.strokes.forEach { stroke -> put(encodeStroke(stroke)) }
                },
            )
            put(
                "notes",
                JSONArray().apply {
                    snapshot.notes.forEach { note -> put(encodeNote(note)) }
                },
            )
        }

    private fun encodeToken(token: TabletopToken): JSONObject =
        JSONObject().apply {
            put("id", token.id)
            put("name", token.name)
            put("x", token.position.x)
            put("y", token.position.y)
            put("widthCells", token.widthCells)
            put("heightCells", token.heightCells)
            put("colorArgb", token.colorArgb)
            put("rotationDegrees", token.rotationDegrees)
            put("orientationMarkerAxis", token.orientationMarkerAxis.name)
            put("movementLocked", token.movementLocked)
            put("scaleLocked", token.scaleLocked)
            put("rotationLocked", token.rotationLocked)
        }

    private fun encodeMeasurement(measurement: MeasurementPath): JSONObject =
        JSONObject().apply {
            put(
                "points",
                JSONArray().apply {
                    measurement.points.forEach { point -> put(encodePoint(point)) }
                },
            )
        }

    private fun encodeStroke(stroke: DrawingStroke): JSONObject =
        JSONObject().apply {
            put("widthWorldUnits", stroke.widthWorldUnits)
            put("colorArgb", stroke.colorArgb)
            put(
                "points",
                JSONArray().apply {
                    stroke.points.forEach { point -> put(encodePoint(point)) }
                },
            )
        }

    private fun encodeNote(note: TabletopNote): JSONObject =
        JSONObject().apply {
            put("id", note.id)
            put("x", note.position.x)
            put("y", note.position.y)
            put("text", note.text)
        }

    private fun encodePoint(point: WorldPoint): JSONObject =
        JSONObject().apply {
            put("x", point.x)
            put("y", point.y)
        }

    private fun decodeTabletop(root: JSONObject): TabletopSceneSnapshot {
        val version = root.optInt("version", 0)
        require(version in 1..TABLETOP_SCHEMA_VERSION) {
            "Unsupported tabletop scene schema: $version"
        }

        val tokensJson = root.optJSONArray("tokens") ?: JSONArray()
        val tokens = buildList {
            for (index in 0 until tokensJson.length()) {
                decodeToken(tokensJson.optJSONObject(index))?.let(::add)
            }
        }

        val strokesJson = root.optJSONArray("strokes") ?: JSONArray()
        val strokes = buildList {
            for (index in 0 until strokesJson.length()) {
                decodeStroke(strokesJson.optJSONObject(index))?.let(::add)
            }
        }

        val notes = if (version >= 2) {
            val notesJson = root.optJSONArray("notes") ?: JSONArray()
            buildList {
                for (index in 0 until notesJson.length()) {
                    decodeNote(notesJson.optJSONObject(index))?.let(::add)
                }
            }
        } else {
            emptyList()
        }

        val measurement = root.optJSONObject("measurement")?.let { json ->
            if (version >= 2) decodeMeasurement(json) else decodeLegacyMeasurement(json)
        }

        return TabletopSceneSnapshot(
            gridKind = enumValueOrDefault(root.optString("gridKind"), GridKind.HEX),
            hexOrientation = enumValueOrDefault(
                root.optString("hexOrientation"),
                HexOrientation.POINTY_TOP,
            ),
            snapEnabled = root.optBoolean("snapEnabled", true),
            cameraCenter = WorldPoint(
                finiteOrDefault(root.optDouble("cameraX"), 0.0),
                finiteOrDefault(root.optDouble("cameraY"), 0.0),
            ),
            pixelsPerWorldUnit = finiteOrDefault(
                root.optDouble("pixelsPerWorldUnit"),
                96.0,
            ).coerceIn(16.0, 320.0),
            displayedUnitsPerCell = finiteOrDefault(
                root.optDouble("displayedUnitsPerCell"),
                5.0,
            ).takeIf { it > 0.0 } ?: 5.0,
            tokens = tokens,
            measurement = measurement,
            strokes = strokes,
            brushColorArgb = if (version >= 2) {
                root.optLong("brushColorArgb", DEFAULT_DRAWING_COLOR_ARGB)
            } else {
                DEFAULT_DRAWING_COLOR_ARGB
            },
            notes = notes,
        )
    }

    private fun decodeToken(json: JSONObject?): TabletopToken? {
        json ?: return null
        val width = finiteOrNull(json.optDouble("widthCells")) ?: return null
        val height = finiteOrNull(json.optDouble("heightCells")) ?: return null
        if (width !in 0.1..100.0 || height !in 0.1..100.0) return null
        val x = finiteOrNull(json.optDouble("x")) ?: return null
        val y = finiteOrNull(json.optDouble("y")) ?: return null
        val rotation = finiteOrNull(json.optDouble("rotationDegrees")) ?: 0.0
        return TabletopToken(
            id = json.optLong("id", 0L).takeIf { it > 0L } ?: return null,
            name = json.optString("name", "Token").take(40),
            position = WorldPoint(x, y),
            widthCells = width,
            heightCells = height,
            colorArgb = json.optLong("colorArgb", TokenColorPreset.BLUE.argb),
            rotationDegrees = normalizeDegrees(rotation),
            orientationMarkerAxis = enumValueOrDefault(
                json.optString("orientationMarkerAxis"),
                TokenOrientationMarkerAxis.MAJOR,
            ),
            movementLocked = json.optBoolean("movementLocked", false),
            scaleLocked = json.optBoolean("scaleLocked", false),
            rotationLocked = json.optBoolean("rotationLocked", false),
        )
    }

    private fun decodeMeasurement(json: JSONObject): MeasurementPath? {
        val pointsJson = json.optJSONArray("points") ?: return null
        val points = buildList {
            for (index in 0 until pointsJson.length()) {
                decodePoint(pointsJson.optJSONObject(index))?.let(::add)
            }
        }
        return points.takeIf { it.isNotEmpty() }?.let(::MeasurementPath)
    }

    private fun decodeLegacyMeasurement(json: JSONObject): MeasurementPath? {
        val start = decodePoint(json.optJSONObject("start")) ?: return null
        val end = decodePoint(json.optJSONObject("end")) ?: return null
        return MeasurementPath(listOf(start, end))
    }

    private fun decodeStroke(json: JSONObject?): DrawingStroke? {
        json ?: return null
        val width = finiteOrNull(json.optDouble("widthWorldUnits"))
            ?.takeIf { it > 0.0 }
            ?: return null
        val pointsJson = json.optJSONArray("points") ?: return null
        val points = buildList {
            for (index in 0 until pointsJson.length()) {
                decodePoint(pointsJson.optJSONObject(index))?.let(::add)
            }
        }
        if (points.size < 2) return null
        return DrawingStroke(
            points = points,
            widthWorldUnits = width,
            colorArgb = json.optLong("colorArgb", DEFAULT_DRAWING_COLOR_ARGB),
        )
    }

    private fun decodeNote(json: JSONObject?): TabletopNote? {
        json ?: return null
        val id = json.optLong("id", 0L).takeIf { it > 0L } ?: return null
        val x = finiteOrNull(json.optDouble("x")) ?: return null
        val y = finiteOrNull(json.optDouble("y")) ?: return null
        return TabletopNote(
            id = id,
            position = WorldPoint(x, y),
            text = json.optString("text", "").take(5_000),
        )
    }

    private fun decodePoint(json: JSONObject?): WorldPoint? {
        json ?: return null
        val x = finiteOrNull(json.optDouble("x")) ?: return null
        val y = finiteOrNull(json.optDouble("y")) ?: return null
        return WorldPoint(x, y)
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        raw: String,
        fallback: T,
    ): T = enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    private fun finiteOrNull(value: Double): Double? = value.takeIf { it.isFinite() }

    private fun finiteOrDefault(value: Double, fallback: Double): Double =
        if (value.isFinite()) value else fallback

    private fun normalizeDegrees(value: Double): Double {
        val normalized = value % 360.0
        return if (normalized < 0.0) normalized + 360.0 else normalized
    }
}
