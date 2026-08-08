package com.adoodguy.androidvtt.tabletop

import android.content.Context
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
    val measurement: MeasurementLine?,
    val strokes: List<DrawingStroke>,
)

/**
 * Persists the non-map tabletop state as one versioned autosave slot.
 *
 * The map already has its own proven URI/geometry store. A later named-scene
 * layer can coordinate this snapshot with the map store without changing the
 * tabletop-state serialization format introduced here.
 */
object TabletopSceneStore {
    private const val PREFS_NAME = "tabletop_scene"
    private const val KEY_AUTOSAVE = "autosave_json"
    private const val SCHEMA_VERSION = 1

    private var appContext: Context? = null
    private var attachedState: TabletopState? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun attachAndRestore(state: TabletopState) {
        attachedState = state
        load()?.let(state::restorePersistentSnapshot)
    }

    fun saveCurrent() {
        val context = appContext ?: return
        val state = attachedState ?: return
        val encoded = encode(state.createPersistentSnapshot()).toString()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AUTOSAVE, encoded)
            .apply()
    }

    private fun load(): TabletopSceneSnapshot? {
        val context = appContext ?: return null
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AUTOSAVE, null)
            ?: return null
        return runCatching { decode(JSONObject(raw)) }.getOrNull()
    }

    private fun encode(snapshot: TabletopSceneSnapshot): JSONObject =
        JSONObject().apply {
            put("version", SCHEMA_VERSION)
            put("gridKind", snapshot.gridKind.name)
            put("hexOrientation", snapshot.hexOrientation.name)
            put("snapEnabled", snapshot.snapEnabled)
            put("cameraX", snapshot.cameraCenter.x)
            put("cameraY", snapshot.cameraCenter.y)
            put("pixelsPerWorldUnit", snapshot.pixelsPerWorldUnit)
            put("displayedUnitsPerCell", snapshot.displayedUnitsPerCell)
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

    private fun encodeMeasurement(measurement: MeasurementLine): JSONObject =
        JSONObject().apply {
            put("start", encodePoint(measurement.start))
            put("end", encodePoint(measurement.end))
        }

    private fun encodeStroke(stroke: DrawingStroke): JSONObject =
        JSONObject().apply {
            put("widthWorldUnits", stroke.widthWorldUnits)
            put(
                "points",
                JSONArray().apply {
                    stroke.points.forEach { point -> put(encodePoint(point)) }
                },
            )
        }

    private fun encodePoint(point: WorldPoint): JSONObject =
        JSONObject().apply {
            put("x", point.x)
            put("y", point.y)
        }

    private fun decode(root: JSONObject): TabletopSceneSnapshot {
        val version = root.optInt("version", 0)
        require(version == SCHEMA_VERSION) { "Unsupported tabletop scene schema: $version" }

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
            measurement = root.optJSONObject("measurement")?.let(::decodeMeasurement),
            strokes = strokes,
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

    private fun decodeMeasurement(json: JSONObject): MeasurementLine? {
        val start = decodePoint(json.optJSONObject("start")) ?: return null
        val end = decodePoint(json.optJSONObject("end")) ?: return null
        return MeasurementLine(start, end)
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
        return DrawingStroke(points = points, widthWorldUnits = width)
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
