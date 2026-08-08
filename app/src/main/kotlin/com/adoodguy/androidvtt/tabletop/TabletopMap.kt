package com.adoodguy.androidvtt.tabletop

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.adoodguy.androidvtt.geometry.WorldPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DEFAULT_MAP_WIDTH_CELLS = 24.0
private const val MAX_MAP_DIMENSION_CELLS = 100_000.0
private const val MAX_DECODED_IMAGE_DIMENSION = 4096
private const val MAP_ROTATION_INCREMENT_DEGREES = 15.0
private const val MAP_ROTATION_MAGNET_THRESHOLD_DEGREES = 3.0
private const val MAP_SCALE_INCREMENT_CELLS = 0.5
private const val MAP_SCALE_MAGNET_THRESHOLD_CELLS = 0.1
private const val MAP_HANDLE_MINIMUM_CELLS = 0.5

enum class MapResizeAxis {
    WIDTH,
    HEIGHT,
}

enum class MapManipulationKind {
    SCALE,
    ROTATION,
}

/**
 * A map is positioned by its center in world/cell coordinates and sized in cells.
 * Rotation is clockwise on screen, with zero degrees meaning the source image is upright.
 */
data class TabletopMapConfiguration(
    val imageUri: String? = null,
    val widthCells: Double = DEFAULT_MAP_WIDTH_CELLS,
    val heightCells: Double = DEFAULT_MAP_WIDTH_CELLS,
    val centerX: Double = 0.0,
    val centerY: Double = 0.0,
    val rotationDegrees: Double = 0.0,
) {
    val hasImage: Boolean get() = !imageUri.isNullOrBlank()
}

object TabletopMapStore {
    private const val PREFS_NAME = "tabletop_map"
    private const val KEY_URI = "image_uri"
    private const val KEY_WIDTH = "width_cells"
    private const val KEY_HEIGHT = "height_cells"
    private const val KEY_CENTER_X = "center_x"
    private const val KEY_CENTER_Y = "center_y"
    private const val KEY_ROTATION = "rotation_degrees"

    private var appContext: Context? = null
    private var resizeBaseWidthCells = DEFAULT_MAP_WIDTH_CELLS
    private var resizeBaseHeightCells = DEFAULT_MAP_WIDTH_CELLS

    var configuration by mutableStateOf(TabletopMapConfiguration())
        private set

    var selected by mutableStateOf(false)
        private set

    var settingsVisible by mutableStateOf(false)
        private set

    var imagePickerRequest by mutableIntStateOf(0)
        private set

    var activeManipulation by mutableStateOf<MapManipulationKind?>(null)
        private set

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        configuration = TabletopMapConfiguration(
            imageUri = prefs.getString(KEY_URI, null),
            widthCells = prefs.getString(KEY_WIDTH, null)?.toDoubleOrNull()
                ?.takeIf(::isValidDimension)
                ?: DEFAULT_MAP_WIDTH_CELLS,
            heightCells = prefs.getString(KEY_HEIGHT, null)?.toDoubleOrNull()
                ?.takeIf(::isValidDimension)
                ?: DEFAULT_MAP_WIDTH_CELLS,
            centerX = prefs.getString(KEY_CENTER_X, null)?.toDoubleOrNull()
                ?.takeIf { it.isFinite() }
                ?: 0.0,
            centerY = prefs.getString(KEY_CENTER_Y, null)?.toDoubleOrNull()
                ?.takeIf { it.isFinite() }
                ?: 0.0,
            rotationDegrees = prefs.getString(KEY_ROTATION, null)?.toDoubleOrNull()
                ?.takeIf { it.isFinite() }
                ?.let(::normalizeDegrees)
                ?: 0.0,
        )
    }

    fun requestImagePicker() {
        imagePickerRequest += 1
    }

    fun importImage(uri: Uri, aspectRatio: Double?) {
        val context = appContext ?: return
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Some providers grant readable URIs without a persistable grant.
        }

        val old = configuration
        val oldUri = old.imageUri?.let(Uri::parse)
        if (oldUri != null && oldUri != uri) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    oldUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // The old provider did not have a persisted grant.
            }
        }

        configuration = if (old.hasImage) {
            old.copy(imageUri = uri.toString())
        } else {
            val safeRatio = aspectRatio?.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
            old.copy(
                imageUri = uri.toString(),
                widthCells = DEFAULT_MAP_WIDTH_CELLS,
                heightCells = (DEFAULT_MAP_WIDTH_CELLS / safeRatio)
                    .coerceIn(0.1, MAX_MAP_DIMENSION_CELLS),
                rotationDegrees = 0.0,
            )
        }
        selected = true
        settingsVisible = false
        activeManipulation = null
        persist()
    }

    fun toggleSelection() {
        if (!configuration.hasImage) return
        selected = !selected
        if (!selected) {
            settingsVisible = false
            activeManipulation = null
        }
    }

    fun select() {
        if (!configuration.hasImage) return
        selected = true
        settingsVisible = false
    }

    fun clearSelection() {
        selected = false
        settingsVisible = false
        activeManipulation = null
    }

    fun openSettings() {
        if (!configuration.hasImage) return
        selected = true
        settingsVisible = true
        activeManipulation = null
    }

    fun closeSettings() {
        settingsVisible = false
    }

    fun updateGeometry(
        widthCells: Double,
        heightCells: Double,
        centerX: Double,
        centerY: Double,
        rotationDegrees: Double,
    ): Boolean {
        if (!isValidDimension(widthCells) || !isValidDimension(heightCells)) return false
        if (!centerX.isFinite() || !centerY.isFinite() || !rotationDegrees.isFinite()) return false
        configuration = configuration.copy(
            widthCells = widthCells,
            heightCells = heightCells,
            centerX = centerX,
            centerY = centerY,
            rotationDegrees = normalizeDegrees(rotationDegrees),
        )
        persist()
        return true
    }

    fun beginMove() {
        if (!configuration.hasImage) return
        selected = true
        settingsVisible = false
        activeManipulation = null
    }

    fun moveByScreenDelta(state: TabletopState, delta: Offset) {
        if (!configuration.hasImage) return
        configuration = configuration.copy(
            centerX = configuration.centerX + delta.x / state.pixelsPerWorldUnit,
            centerY = configuration.centerY + delta.y / state.pixelsPerWorldUnit,
        )
    }

    fun finishMove(state: TabletopState) {
        if (!configuration.hasImage) return
        val center = WorldPoint(configuration.centerX, configuration.centerY)
        val snapped = state.snappedWorldPoint(state.worldToScreen(center))
        configuration = configuration.copy(centerX = snapped.x, centerY = snapped.y)
        persist()
    }

    fun beginResize() {
        if (!configuration.hasImage) return
        selected = true
        settingsVisible = false
        resizeBaseWidthCells = configuration.widthCells
        resizeBaseHeightCells = configuration.heightCells
        activeManipulation = MapManipulationKind.SCALE
    }

    fun resizeFromScreenPoint(
        state: TabletopState,
        axis: MapResizeAxis,
        screenPoint: Offset,
    ) {
        if (!configuration.hasImage) return
        val center = state.worldToScreen(WorldPoint(configuration.centerX, configuration.centerY))
        val deltaX = (screenPoint.x - center.x).toDouble()
        val deltaY = (screenPoint.y - center.y).toDouble()
        val radians = Math.toRadians(configuration.rotationDegrees)
        val localX = deltaX * cos(radians) + deltaY * sin(radians)
        val localY = -deltaX * sin(radians) + deltaY * cos(radians)
        val pixelsPerCell = state.pixelsPerWorldUnit * state.cellSizeWorldUnits

        val rawAxisCells = when (axis) {
            MapResizeAxis.WIDTH -> 2.0 * abs(localX) / pixelsPerCell
            MapResizeAxis.HEIGHT -> 2.0 * abs(localY) / pixelsPerCell
        }
        val adjustedAxisCells = magneticScale(rawAxisCells, state.snapEnabled)
        val baseAxisCells = when (axis) {
            MapResizeAxis.WIDTH -> resizeBaseWidthCells
            MapResizeAxis.HEIGHT -> resizeBaseHeightCells
        }
        if (baseAxisCells <= 0.0 || !baseAxisCells.isFinite()) return

        val minimumScaleFactor = maxOf(
            MAP_HANDLE_MINIMUM_CELLS / resizeBaseWidthCells,
            MAP_HANDLE_MINIMUM_CELLS / resizeBaseHeightCells,
        )
        val maximumScaleFactor = minOf(
            MAX_MAP_DIMENSION_CELLS / resizeBaseWidthCells,
            MAX_MAP_DIMENSION_CELLS / resizeBaseHeightCells,
        )
        val scaleFactor = (adjustedAxisCells / baseAxisCells)
            .coerceIn(minimumScaleFactor, maximumScaleFactor)

        configuration = configuration.copy(
            widthCells = resizeBaseWidthCells * scaleFactor,
            heightCells = resizeBaseHeightCells * scaleFactor,
        )
        activeManipulation = MapManipulationKind.SCALE
    }

    fun beginRotation() {
        if (!configuration.hasImage) return
        selected = true
        settingsVisible = false
        activeManipulation = MapManipulationKind.ROTATION
    }

    fun rotateFromScreenPoint(state: TabletopState, screenPoint: Offset) {
        if (!configuration.hasImage) return
        val center = state.worldToScreen(WorldPoint(configuration.centerX, configuration.centerY))
        val deltaX = (screenPoint.x - center.x).toDouble()
        val deltaY = (screenPoint.y - center.y).toDouble()
        if (abs(deltaX) < 0.000_001 && abs(deltaY) < 0.000_001) return

        val raw = normalizeDegrees(Math.toDegrees(atan2(deltaX, -deltaY)))
        configuration = configuration.copy(
            rotationDegrees = magneticRotation(raw, state.snapEnabled),
        )
        activeManipulation = MapManipulationKind.ROTATION
    }

    fun finishManipulation() {
        activeManipulation = null
        persist()
    }

    fun removeMap() {
        val context = appContext
        val uri = configuration.imageUri?.let(Uri::parse)
        if (context != null && uri != null) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // No persisted grant existed; clearing the stored configuration is enough.
            }
        }
        configuration = TabletopMapConfiguration()
        clearSelection()
        persist()
    }

    private fun persist() {
        val context = appContext ?: return
        val config = configuration
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (config.imageUri == null) remove(KEY_URI) else putString(KEY_URI, config.imageUri)
                putString(KEY_WIDTH, config.widthCells.toString())
                putString(KEY_HEIGHT, config.heightCells.toString())
                putString(KEY_CENTER_X, config.centerX.toString())
                putString(KEY_CENTER_Y, config.centerY.toString())
                putString(KEY_ROTATION, config.rotationDegrees.toString())
            }
            .apply()
    }

    private fun isValidDimension(value: Double): Boolean =
        value.isFinite() && value in 0.1..MAX_MAP_DIMENSION_CELLS

    private fun magneticScale(value: Double, enabled: Boolean): Double {
        if (!enabled) return value
        val nearest = round(value / MAP_SCALE_INCREMENT_CELLS) * MAP_SCALE_INCREMENT_CELLS
        return if (abs(value - nearest) <= MAP_SCALE_MAGNET_THRESHOLD_CELLS) nearest else value
    }

    private fun magneticRotation(value: Double, enabled: Boolean): Double {
        if (!enabled) return normalizeDegrees(value)
        val nearest = round(value / MAP_ROTATION_INCREMENT_DEGREES) * MAP_ROTATION_INCREMENT_DEGREES
        return if (abs(value - nearest) <= MAP_ROTATION_MAGNET_THRESHOLD_DEGREES) {
            normalizeDegrees(nearest)
        } else {
            normalizeDegrees(value)
        }
    }

    private fun normalizeDegrees(degrees: Double): Double {
        val normalized = degrees % 360.0
        return if (normalized < 0.0) normalized + 360.0 else normalized
    }
}

fun readMapImageAspectRatio(context: Context, uri: Uri): Double? =
    runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            null
        } else {
            options.outWidth.toDouble() / options.outHeight.toDouble()
        }
    }.getOrNull()

@Composable
fun rememberTabletopMapImage(imageUri: String?): ImageBitmap? {
    val context = LocalContext.current
    var image by remember(imageUri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(imageUri) {
        image = withContext(Dispatchers.IO) {
            runCatching {
                imageUri?.let { decodeTabletopMap(context, Uri.parse(it)) }
            }.getOrNull()
        }
    }
    return image
}

private fun decodeTabletopMap(context: Context, uri: Uri): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    var sampledWidth = bounds.outWidth
    var sampledHeight = bounds.outHeight
    while (max(sampledWidth, sampledHeight) > MAX_DECODED_IMAGE_DIMENSION) {
        sampleSize *= 2
        sampledWidth = bounds.outWidth / sampleSize
        sampledHeight = bounds.outHeight / sampleSize
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
    }
}

fun DrawScope.drawTabletopMap(
    image: ImageBitmap?,
    configuration: TabletopMapConfiguration,
    state: TabletopState,
) {
    if (image == null || !configuration.hasImage) return

    val center = state.worldToScreen(WorldPoint(configuration.centerX, configuration.centerY))
    val width = (configuration.widthCells * state.cellSizeWorldUnits * state.pixelsPerWorldUnit)
        .roundToInt()
        .coerceAtLeast(1)
    val height = (configuration.heightCells * state.cellSizeWorldUnits * state.pixelsPerWorldUnit)
        .roundToInt()
        .coerceAtLeast(1)
    val left = (center.x - width / 2f).roundToInt()
    val top = (center.y - height / 2f).roundToInt()

    rotate(configuration.rotationDegrees.toFloat(), pivot = center) {
        drawImage(
            image = image,
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(width, height),
            filterQuality = FilterQuality.Medium,
        )
    }
}
