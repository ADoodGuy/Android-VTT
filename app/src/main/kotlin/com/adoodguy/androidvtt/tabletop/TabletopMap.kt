package com.adoodguy.androidvtt.tabletop

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.adoodguy.androidvtt.geometry.GridKind
import com.adoodguy.androidvtt.geometry.WorldPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

private const val DEFAULT_MAP_WIDTH_CELLS = 24.0
private const val MAX_MAP_DIMENSION_CELLS = 100_000.0
private const val MAX_DECODED_IMAGE_DIMENSION = 4096
private const val MAP_ROTATION_INCREMENT_DEGREES = 15.0
private const val MAP_ROTATION_MAGNET_THRESHOLD_DEGREES = 3.0
private const val MAP_SCALE_INCREMENT_CELLS = 0.5
private const val MAP_SCALE_MAGNET_THRESHOLD_CELLS = 0.1
private const val MAP_HANDLE_MINIMUM_CELLS = 0.1
private const val ALIGNMENT_GUIDE_RADIUS_CELLS = 4.0

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
 *
 * snapAnchorU/V store the persistent alignment point on the source image as normalized
 * offsets from its center. The normal selected-map controller uses a separate temporary
 * anchor so placing a convenient controller does not damage the saved alignment phase.
 */
data class TabletopMapConfiguration(
    val imageUri: String? = null,
    val widthCells: Double = DEFAULT_MAP_WIDTH_CELLS,
    val heightCells: Double = DEFAULT_MAP_WIDTH_CELLS,
    val centerX: Double = 0.0,
    val centerY: Double = 0.0,
    val rotationDegrees: Double = 0.0,
    val snapAnchorU: Double = 0.0,
    val snapAnchorV: Double = 0.0,
    val movementLocked: Boolean = false,
    val scaleLocked: Boolean = false,
    val rotationLocked: Boolean = false,
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
    private const val KEY_SNAP_ANCHOR_U = "snap_anchor_u"
    private const val KEY_SNAP_ANCHOR_V = "snap_anchor_v"
    private const val KEY_MOVEMENT_LOCKED = "movement_locked"
    private const val KEY_SCALE_LOCKED = "scale_locked"
    private const val KEY_ROTATION_LOCKED = "rotation_locked"

    private var appContext: Context? = null
    private var resizeBaseWidthCells = DEFAULT_MAP_WIDTH_CELLS
    private var resizeBaseHeightCells = DEFAULT_MAP_WIDTH_CELLS
    private var manipulationAnchorWorld: WorldPoint? = null
    private var manipulationAnchorU = 0.0
    private var manipulationAnchorV = 0.0
    private var alignmentSnapshot: TabletopMapConfiguration? = null

    var configuration by mutableStateOf(TabletopMapConfiguration())
        private set

    var selected by mutableStateOf(false)
        private set

    var settingsVisible by mutableStateOf(false)
        private set

    var alignmentVisible by mutableStateOf(false)
        private set

    var imagePickerRequest by mutableIntStateOf(0)
        private set

    var activeManipulation by mutableStateOf<MapManipulationKind?>(null)
        private set

    var controlAnchorU by mutableDoubleStateOf(0.0)
        private set
    var controlAnchorV by mutableDoubleStateOf(0.0)
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
            snapAnchorU = prefs.getString(KEY_SNAP_ANCHOR_U, null)?.toDoubleOrNull()
                ?.takeIf(::isValidAnchorCoordinate)
                ?: 0.0,
            snapAnchorV = prefs.getString(KEY_SNAP_ANCHOR_V, null)?.toDoubleOrNull()
                ?.takeIf(::isValidAnchorCoordinate)
                ?: 0.0,
            movementLocked = prefs.getBoolean(KEY_MOVEMENT_LOCKED, false),
            scaleLocked = prefs.getBoolean(KEY_SCALE_LOCKED, false),
            rotationLocked = prefs.getBoolean(KEY_ROTATION_LOCKED, false),
        )
        controlAnchorU = configuration.snapAnchorU
        controlAnchorV = configuration.snapAnchorV
    }

    fun restoreSceneConfiguration(sceneConfiguration: TabletopMapConfiguration) {
        configuration = sceneConfiguration.copy(
            widthCells = sceneConfiguration.widthCells
                .takeIf(::isValidDimension) ?: DEFAULT_MAP_WIDTH_CELLS,
            heightCells = sceneConfiguration.heightCells
                .takeIf(::isValidDimension) ?: DEFAULT_MAP_WIDTH_CELLS,
            centerX = sceneConfiguration.centerX.takeIf { it.isFinite() } ?: 0.0,
            centerY = sceneConfiguration.centerY.takeIf { it.isFinite() } ?: 0.0,
            rotationDegrees = sceneConfiguration.rotationDegrees
                .takeIf { it.isFinite() }
                ?.let(::normalizeDegrees)
                ?: 0.0,
            snapAnchorU = sceneConfiguration.snapAnchorU
                .takeIf(::isValidAnchorCoordinate) ?: 0.0,
            snapAnchorV = sceneConfiguration.snapAnchorV
                .takeIf(::isValidAnchorCoordinate) ?: 0.0,
        )
        resizeBaseWidthCells = configuration.widthCells
        resizeBaseHeightCells = configuration.heightCells
        controlAnchorU = configuration.snapAnchorU
        controlAnchorV = configuration.snapAnchorV
        selected = false
        settingsVisible = false
        alignmentVisible = false
        alignmentSnapshot = null
        activeManipulation = null
        manipulationAnchorWorld = null
        manipulationAnchorU = 0.0
        manipulationAnchorV = 0.0
        persist()
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
        if (
            oldUri != null &&
            oldUri != uri &&
            !TabletopSceneStore.isMapUriReferencedByOtherScene(oldUri.toString())
        ) {
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
                snapAnchorU = 0.0,
                snapAnchorV = 0.0,
            )
        }
        controlAnchorU = configuration.snapAnchorU
        controlAnchorV = configuration.snapAnchorV
        selected = true
        settingsVisible = false
        alignmentVisible = false
        alignmentSnapshot = null
        activeManipulation = null
        persist()
    }

    fun selectAtScreenPoint(state: TabletopState, screenPoint: Offset) {
        if (!configuration.hasImage || alignmentVisible) return
        val snapped = nearestGridAnchor(state, state.screenToWorld(screenPoint))
        val local = normalizedMapCoordinates(configuration, state, snapped)
        controlAnchorU = local.first
        controlAnchorV = local.second
        selected = true
        settingsVisible = false
        activeManipulation = null
    }

    fun select() {
        if (!configuration.hasImage) return
        if (!selected) {
            controlAnchorU = configuration.snapAnchorU
            controlAnchorV = configuration.snapAnchorV
        }
        selected = true
        settingsVisible = false
    }

    fun clearSelection() {
        if (alignmentVisible) return
        selected = false
        settingsVisible = false
        activeManipulation = null
        manipulationAnchorWorld = null
    }

    fun openSettings() {
        if (!configuration.hasImage || alignmentVisible) return
        select()
        settingsVisible = true
        activeManipulation = null
    }

    fun closeSettings() {
        settingsVisible = false
    }

    fun openAlignmentAssistant() {
        if (!configuration.hasImage) return
        alignmentSnapshot = configuration
        alignmentVisible = true
        settingsVisible = false
        selected = true
        activeManipulation = null
        manipulationAnchorWorld = null
    }

    fun finishAlignment() {
        if (!alignmentVisible) return
        alignmentVisible = false
        alignmentSnapshot = null
        controlAnchorU = configuration.snapAnchorU
        controlAnchorV = configuration.snapAnchorV
        activeManipulation = null
        manipulationAnchorWorld = null
        persist()
    }

    fun cancelAlignment() {
        alignmentSnapshot?.let { configuration = it }
        alignmentVisible = false
        alignmentSnapshot = null
        controlAnchorU = configuration.snapAnchorU
        controlAnchorV = configuration.snapAnchorV
        activeManipulation = null
        manipulationAnchorWorld = null
        selected = true
        persist()
    }

    fun resetSnapAnchorToCenter() {
        configuration = configuration.copy(
            snapAnchorU = 0.0,
            snapAnchorV = 0.0,
        )
    }

    fun toggleMovementLock() {
        configuration = configuration.copy(movementLocked = !configuration.movementLocked)
        activeManipulation = null
        persist()
    }

    fun toggleScaleLock() {
        configuration = configuration.copy(scaleLocked = !configuration.scaleLocked)
        activeManipulation = null
        manipulationAnchorWorld = null
        persist()
    }

    fun toggleRotationLock() {
        configuration = configuration.copy(rotationLocked = !configuration.rotationLocked)
        activeManipulation = null
        manipulationAnchorWorld = null
        persist()
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
        if (!configuration.hasImage || configuration.movementLocked) return
        selected = true
        settingsVisible = false
        activeManipulation = null
    }

    fun moveByScreenDelta(state: TabletopState, delta: Offset) {
        if (!configuration.hasImage || configuration.movementLocked) return

        if (alignmentVisible) {
            val radians = Math.toRadians(configuration.rotationDegrees)
            val worldDeltaX = delta.x / state.pixelsPerWorldUnit
            val worldDeltaY = delta.y / state.pixelsPerWorldUnit
            val localDeltaX = worldDeltaX * cos(radians) + worldDeltaY * sin(radians)
            val localDeltaY = -worldDeltaX * sin(radians) + worldDeltaY * cos(radians)
            val widthWorld = configuration.widthCells * state.cellSizeWorldUnits
            val heightWorld = configuration.heightCells * state.cellSizeWorldUnits
            if (widthWorld <= 0.0 || heightWorld <= 0.0) return

            configuration = configuration.copy(
                snapAnchorU = (configuration.snapAnchorU + localDeltaX / widthWorld)
                    .coerceIn(-0.5, 0.5),
                snapAnchorV = (configuration.snapAnchorV + localDeltaY / heightWorld)
                    .coerceIn(-0.5, 0.5),
            )
            return
        }

        configuration = configuration.copy(
            centerX = configuration.centerX + delta.x / state.pixelsPerWorldUnit,
            centerY = configuration.centerY + delta.y / state.pixelsPerWorldUnit,
        )
    }

    fun finishMove(state: TabletopState) {
        if (!configuration.hasImage || configuration.movementLocked) return
        if (alignmentVisible) {
            snapConfiguredAnchorToGrid(state, force = true)
        } else {
            snapControlAnchorToGrid(state)
        }
        persist()
    }

    fun beginResize() {
        if (!configuration.hasImage || configuration.scaleLocked) return
        selected = true
        settingsVisible = false
        resizeBaseWidthCells = configuration.widthCells
        resizeBaseHeightCells = configuration.heightCells
        manipulationAnchorU = controllerAnchorU()
        manipulationAnchorV = controllerAnchorV()
        manipulationAnchorWorld = controllerAnchorWorldInternal(state = null)
        activeManipulation = MapManipulationKind.SCALE
    }

    fun resizeFromScreenPoint(
        state: TabletopState,
        axis: MapResizeAxis,
        screenPoint: Offset,
    ) {
        if (!configuration.hasImage || configuration.scaleLocked) return
        if (manipulationAnchorWorld == null) {
            manipulationAnchorU = controllerAnchorU()
            manipulationAnchorV = controllerAnchorV()
            manipulationAnchorWorld = controllerAnchorWorld(state)
        }
        val fixedAnchorWorld = manipulationAnchorWorld ?: return
        val anchorScreen = state.worldToScreen(fixedAnchorWorld)
        val deltaX = (screenPoint.x - anchorScreen.x).toDouble()
        val deltaY = (screenPoint.y - anchorScreen.y).toDouble()
        val radians = Math.toRadians(configuration.rotationDegrees)
        val localX = deltaX * cos(radians) + deltaY * sin(radians)
        val localY = -deltaX * sin(radians) + deltaY * cos(radians)
        val pixelsPerCell = state.pixelsPerWorldUnit * state.cellSizeWorldUnits

        val rawScaleFactor = when (axis) {
            MapResizeAxis.WIDTH -> {
                val side = sign(localX).takeIf { it != 0.0 } ?: 1.0
                val baseDistanceCells = abs(
                    (side * 0.5 - manipulationAnchorU) * resizeBaseWidthCells,
                )
                if (baseDistanceCells < 0.000_001) return
                abs(localX) / pixelsPerCell / baseDistanceCells
            }

            MapResizeAxis.HEIGHT -> {
                val side = sign(localY).takeIf { it != 0.0 } ?: 1.0
                val baseDistanceCells = abs(
                    (side * 0.5 - manipulationAnchorV) * resizeBaseHeightCells,
                )
                if (baseDistanceCells < 0.000_001) return
                abs(localY) / pixelsPerCell / baseDistanceCells
            }
        }

        val baseAxisCells = when (axis) {
            MapResizeAxis.WIDTH -> resizeBaseWidthCells
            MapResizeAxis.HEIGHT -> resizeBaseHeightCells
        }
        val rawAxisCells = baseAxisCells * rawScaleFactor
        val adjustedAxisCells = magneticScale(rawAxisCells, state.snapEnabled)
        val scaleFactor = boundedScaleFactor(adjustedAxisCells / baseAxisCells)

        val resized = configuration.copy(
            widthCells = resizeBaseWidthCells * scaleFactor,
            heightCells = resizeBaseHeightCells * scaleFactor,
        )
        configuration = recenterMapForFixedAnchor(
            resizedOrRotated = resized,
            fixedAnchorWorld = fixedAnchorWorld,
            state = state,
            anchorU = manipulationAnchorU,
            anchorV = manipulationAnchorV,
        )
        activeManipulation = MapManipulationKind.SCALE
    }

    private fun boundedScaleFactor(requested: Double): Double {
        val minimumScaleFactor = maxOf(
            MAP_HANDLE_MINIMUM_CELLS / resizeBaseWidthCells,
            MAP_HANDLE_MINIMUM_CELLS / resizeBaseHeightCells,
        )
        val maximumScaleFactor = minOf(
            MAX_MAP_DIMENSION_CELLS / resizeBaseWidthCells,
            MAX_MAP_DIMENSION_CELLS / resizeBaseHeightCells,
        )
        return requested.coerceIn(minimumScaleFactor, maximumScaleFactor)
    }

    fun beginRotation() {
        if (!configuration.hasImage || configuration.rotationLocked) return
        selected = true
        settingsVisible = false
        manipulationAnchorU = controllerAnchorU()
        manipulationAnchorV = controllerAnchorV()
        manipulationAnchorWorld = null
        activeManipulation = MapManipulationKind.ROTATION
    }

    fun rotateFromScreenPoint(state: TabletopState, screenPoint: Offset) {
        if (!configuration.hasImage || configuration.rotationLocked) return
        val fixedAnchorWorld = manipulationAnchorWorld
            ?: controllerAnchorWorld(state).also { manipulationAnchorWorld = it }
        val center = state.worldToScreen(WorldPoint(configuration.centerX, configuration.centerY))
        val deltaX = (screenPoint.x - center.x).toDouble()
        val deltaY = (screenPoint.y - center.y).toDouble()
        if (abs(deltaX) < 0.000_001 && abs(deltaY) < 0.000_001) return

        val raw = normalizeDegrees(Math.toDegrees(atan2(deltaX, -deltaY)))
        val rotated = configuration.copy(
            rotationDegrees = magneticRotation(raw, state.snapEnabled),
        )
        configuration = recenterMapForFixedAnchor(
            resizedOrRotated = rotated,
            fixedAnchorWorld = fixedAnchorWorld,
            state = state,
            anchorU = manipulationAnchorU,
            anchorV = manipulationAnchorV,
        )
        activeManipulation = MapManipulationKind.ROTATION
    }

    fun finishManipulation() {
        activeManipulation = null
        manipulationAnchorWorld = null
        persist()
    }

    fun snapAnchorWorld(state: TabletopState): WorldPoint =
        mapAnchorWorld(
            configuration = configuration,
            state = state,
            anchorU = configuration.snapAnchorU,
            anchorV = configuration.snapAnchorV,
        )

    fun controllerAnchorWorld(state: TabletopState): WorldPoint =
        if (alignmentVisible) {
            snapAnchorWorld(state)
        } else {
            mapAnchorWorld(
                configuration = configuration,
                state = state,
                anchorU = controlAnchorU,
                anchorV = controlAnchorV,
            )
        }

    fun controllerAnchorU(): Double =
        if (alignmentVisible) configuration.snapAnchorU else controlAnchorU

    fun controllerAnchorV(): Double =
        if (alignmentVisible) configuration.snapAnchorV else controlAnchorV

    private fun controllerAnchorWorldInternal(state: TabletopState?): WorldPoint? =
        state?.let(::controllerAnchorWorld)

    private fun snapConfiguredAnchorToGrid(state: TabletopState, force: Boolean) {
        if (!force && !state.snapEnabled) return
        val anchor = snapAnchorWorld(state)
        val snapped = nearestGridAnchor(state, anchor)
        configuration = configuration.copy(
            centerX = configuration.centerX + (snapped.x - anchor.x),
            centerY = configuration.centerY + (snapped.y - anchor.y),
        )
    }

    private fun snapControlAnchorToGrid(state: TabletopState) {
        if (!state.snapEnabled) return
        val anchor = controllerAnchorWorld(state)
        val snapped = nearestGridAnchor(state, anchor)
        configuration = configuration.copy(
            centerX = configuration.centerX + (snapped.x - anchor.x),
            centerY = configuration.centerY + (snapped.y - anchor.y),
        )
    }

    private fun nearestGridAnchor(state: TabletopState, point: WorldPoint): WorldPoint =
        when (state.gridKind) {
            GridKind.SQUARE -> state.squareGrid.snapToNearestAnchor(point)
            GridKind.HEX -> state.hexGrid.snapToNearestAnchor(point)
        }

    private fun normalizedMapCoordinates(
        configuration: TabletopMapConfiguration,
        state: TabletopState,
        point: WorldPoint,
    ): Pair<Double, Double> {
        val deltaX = point.x - configuration.centerX
        val deltaY = point.y - configuration.centerY
        val radians = Math.toRadians(configuration.rotationDegrees)
        val localX = deltaX * cos(radians) + deltaY * sin(radians)
        val localY = -deltaX * sin(radians) + deltaY * cos(radians)
        val widthWorld = configuration.widthCells * state.cellSizeWorldUnits
        val heightWorld = configuration.heightCells * state.cellSizeWorldUnits
        if (widthWorld <= 0.0 || heightWorld <= 0.0) return 0.0 to 0.0
        return localX / widthWorld to localY / heightWorld
    }

    private fun recenterMapForFixedAnchor(
        resizedOrRotated: TabletopMapConfiguration,
        fixedAnchorWorld: WorldPoint,
        state: TabletopState,
        anchorU: Double,
        anchorV: Double,
    ): TabletopMapConfiguration {
        val localX = anchorU * resizedOrRotated.widthCells * state.cellSizeWorldUnits
        val localY = anchorV * resizedOrRotated.heightCells * state.cellSizeWorldUnits
        val radians = Math.toRadians(resizedOrRotated.rotationDegrees)
        val rotatedX = localX * cos(radians) - localY * sin(radians)
        val rotatedY = localX * sin(radians) + localY * cos(radians)
        return resizedOrRotated.copy(
            centerX = fixedAnchorWorld.x - rotatedX,
            centerY = fixedAnchorWorld.y - rotatedY,
        )
    }

    fun removeMap() {
        val context = appContext
        val uri = configuration.imageUri?.let(Uri::parse)
        if (
            context != null &&
            uri != null &&
            !TabletopSceneStore.isMapUriReferencedByOtherScene(uri.toString())
        ) {
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
        controlAnchorU = 0.0
        controlAnchorV = 0.0
        alignmentVisible = false
        alignmentSnapshot = null
        selected = false
        settingsVisible = false
        activeManipulation = null
        manipulationAnchorWorld = null
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
                putString(KEY_SNAP_ANCHOR_U, config.snapAnchorU.toString())
                putString(KEY_SNAP_ANCHOR_V, config.snapAnchorV.toString())
                putBoolean(KEY_MOVEMENT_LOCKED, config.movementLocked)
                putBoolean(KEY_SCALE_LOCKED, config.scaleLocked)
                putBoolean(KEY_ROTATION_LOCKED, config.rotationLocked)
            }
            .apply()
    }

    private fun isValidDimension(value: Double): Boolean =
        value.isFinite() && value in 0.1..MAX_MAP_DIMENSION_CELLS

    private fun isValidAnchorCoordinate(value: Double): Boolean =
        value.isFinite() && value in -0.5..0.5

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

private fun mapAnchorWorld(
    configuration: TabletopMapConfiguration,
    state: TabletopState,
    anchorU: Double,
    anchorV: Double,
): WorldPoint {
    val localX = anchorU * configuration.widthCells * state.cellSizeWorldUnits
    val localY = anchorV * configuration.heightCells * state.cellSizeWorldUnits
    val radians = Math.toRadians(configuration.rotationDegrees)
    return WorldPoint(
        x = configuration.centerX + localX * cos(radians) - localY * sin(radians),
        y = configuration.centerY + localX * sin(radians) + localY * cos(radians),
    )
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

    if (TabletopMapStore.alignmentVisible) {
        drawMapAlignmentGuides(configuration, state)
    }
}

private fun DrawScope.drawMapAlignmentGuides(
    configuration: TabletopMapConfiguration,
    state: TabletopState,
) {
    val anchorWorld = TabletopMapStore.snapAnchorWorld(state)
    val anchor = state.worldToScreen(anchorWorld)
    val pixelsPerCell = (state.pixelsPerWorldUnit * state.cellSizeWorldUnits).toFloat()
    val radius = pixelsPerCell * ALIGNMENT_GUIDE_RADIUS_CELLS.toFloat()
    val guideColor = Color(0xCCFF9800)
    val rulerColor = Color(0xFFF57C00)
    val guideStroke = (2f * density).coerceAtLeast(2f)

    val visibleBounds = state.transform.visibleWorldRect()
    when (state.gridKind) {
        GridKind.SQUARE -> {
            state.squareGrid.verticalLines(visibleBounds).forEach { x ->
                val screenX = state.worldToScreen(WorldPoint(x, anchorWorld.y)).x
                if (abs(screenX - anchor.x) <= radius) {
                    drawLine(
                        color = guideColor,
                        start = Offset(screenX, anchor.y - radius),
                        end = Offset(screenX, anchor.y + radius),
                        strokeWidth = guideStroke,
                    )
                }
            }
            state.squareGrid.horizontalLines(visibleBounds).forEach { y ->
                val screenY = state.worldToScreen(WorldPoint(anchorWorld.x, y)).y
                if (abs(screenY - anchor.y) <= radius) {
                    drawLine(
                        color = guideColor,
                        start = Offset(anchor.x - radius, screenY),
                        end = Offset(anchor.x + radius, screenY),
                        strokeWidth = guideStroke,
                    )
                }
            }
        }

        GridKind.HEX -> {
            state.hexGrid.visibleCells(visibleBounds).forEach { coordinate ->
                val cellCenter = state.worldToScreen(state.hexGrid.centerOf(coordinate))
                if (
                    abs(cellCenter.x - anchor.x) <= radius + pixelsPerCell &&
                    abs(cellCenter.y - anchor.y) <= radius + pixelsPerCell
                ) {
                    val corners = state.hexGrid.corners(state.hexGrid.centerOf(coordinate))
                    val path = Path()
                    corners.forEachIndexed { index, point ->
                        val screen = state.worldToScreen(point)
                        if (index == 0) {
                            path.moveTo(screen.x, screen.y)
                        } else {
                            path.lineTo(screen.x, screen.y)
                        }
                    }
                    path.close()
                    drawPath(path, guideColor, style = Stroke(guideStroke))
                }
            }
        }
    }

    drawLine(
        color = rulerColor,
        start = anchor,
        end = Offset(anchor.x + radius, anchor.y),
        strokeWidth = guideStroke * 1.5f,
    )
    drawLine(
        color = rulerColor,
        start = anchor,
        end = Offset(anchor.x, anchor.y + radius),
        strokeWidth = guideStroke * 1.5f,
    )

    val tickHalfLength = 8f * density
    for (index in 1..ALIGNMENT_GUIDE_RADIUS_CELLS.toInt()) {
        val offset = pixelsPerCell * index
        drawLine(
            color = rulerColor,
            start = Offset(anchor.x + offset, anchor.y - tickHalfLength),
            end = Offset(anchor.x + offset, anchor.y + tickHalfLength),
            strokeWidth = guideStroke,
        )
        drawLine(
            color = rulerColor,
            start = Offset(anchor.x - tickHalfLength, anchor.y + offset),
            end = Offset(anchor.x + tickHalfLength, anchor.y + offset),
            strokeWidth = guideStroke,
        )
    }

    drawCircle(
        color = Color(0xFFFFC107),
        radius = 10f * density,
        center = anchor,
        style = Stroke(width = 3f * density),
    )
    drawLine(
        color = Color.White,
        start = Offset(anchor.x - 14f * density, anchor.y),
        end = Offset(anchor.x + 14f * density, anchor.y),
        strokeWidth = 2f * density,
    )
    drawLine(
        color = Color.White,
        start = Offset(anchor.x, anchor.y - 14f * density),
        end = Offset(anchor.x, anchor.y + 14f * density),
        strokeWidth = 2f * density,
    )
}
