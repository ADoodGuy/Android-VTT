package com.adoodguy.androidvtt.tabletop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.adoodguy.androidvtt.geometry.GridKind
import com.adoodguy.androidvtt.geometry.HexGridGeometry
import com.adoodguy.androidvtt.geometry.HexOrientation
import com.adoodguy.androidvtt.geometry.ScreenPoint
import com.adoodguy.androidvtt.geometry.ScreenVector
import com.adoodguy.androidvtt.geometry.SquareGridGeometry
import com.adoodguy.androidvtt.geometry.UnitScale
import com.adoodguy.androidvtt.geometry.ViewportSize
import com.adoodguy.androidvtt.geometry.ViewportTransform
import com.adoodguy.androidvtt.geometry.WorldPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

class TabletopState {
    var tool by mutableStateOf(TabletopTool.PAN)
    var gridKind by mutableStateOf(GridKind.HEX)
    var hexOrientation by mutableStateOf(HexOrientation.POINTY_TOP)
    var snapEnabled by mutableStateOf(true)
    var viewportSize by mutableStateOf(IntSize.Zero)

    var cameraCenter by mutableStateOf(WorldPoint.Zero)
    var pixelsPerWorldUnit by mutableDoubleStateOf(96.0)
        private set

    val cellSizeWorldUnits: Double = 1.0
    val brushWidthWorldUnits: Double = 0.065

    val unitScalePresets = listOf(1.0, 5.0, 10.0)
    var displayedUnitsPerCell by mutableDoubleStateOf(5.0)
        private set
    val unitScale: UnitScale
        get() = UnitScale(displayedUnitsPerCell / cellSizeWorldUnits, "ft")

    private var nextTokenId = 2L

    val tokens = mutableStateListOf(
        TabletopToken(
            id = 1L,
            name = "Token 1",
            position = WorldPoint.Zero,
            widthCells = TokenSizePreset.ONE_BY_ONE.widthCells,
            heightCells = TokenSizePreset.ONE_BY_ONE.heightCells,
            colorArgb = TokenColorPreset.BLUE.argb,
            rotationDegrees = 0.0,
            orientationMarkerAxis = TokenOrientationMarkerAxis.MAJOR,
        ),
    )

    var selectedTokenId by mutableStateOf<Long?>(null)
        private set
    var tokenMenuVisible by mutableStateOf(false)
        private set
    var activeTokenManipulation by mutableStateOf<ActiveTokenManipulation?>(null)
        private set

    val selectedToken: TabletopToken?
        get() = selectedTokenId?.let(::tokenById)

    var measurement by mutableStateOf<MeasurementPath?>(null)
        private set
    var selectedMeasurementMarkerIndex by mutableStateOf<Int?>(null)
        private set

    val strokes = mutableStateListOf<DrawingStroke>()
    var activeStroke by mutableStateOf<DrawingStroke?>(null)
        private set
    var drawingMode by mutableStateOf(DrawingMode.BRUSH)
        private set
    var brushColorArgb by mutableStateOf(DEFAULT_DRAWING_COLOR_ARGB)
        private set

    private var nextNoteId = 1L
    val notes = mutableStateListOf<TabletopNote>()

    val squareGrid: SquareGridGeometry
        get() = SquareGridGeometry(cellSizeWorldUnits)
    val hexGrid: HexGridGeometry
        get() = HexGridGeometry(cellSizeWorldUnits, hexOrientation)

    init {
        TabletopSceneStore.attachAndRestore(this)
    }

    val transform: ViewportTransform
        get() = ViewportTransform(
            cameraCenter = cameraCenter,
            pixelsPerWorldUnit = pixelsPerWorldUnit,
            viewport = ViewportSize(viewportSize.width.toDouble(), viewportSize.height.toDouble()),
        )

    fun worldToScreen(point: WorldPoint): Offset {
        val result = transform.worldToScreen(point)
        return Offset(result.x.toFloat(), result.y.toFloat())
    }

    fun screenToWorld(point: Offset): WorldPoint =
        transform.screenToWorld(ScreenPoint(point.x.toDouble(), point.y.toDouble()))

    fun snappedWorldPoint(screenPoint: Offset): WorldPoint =
        snapWorldPoint(screenToWorld(screenPoint))

    private fun snapWorldPoint(world: WorldPoint): WorldPoint {
        if (!snapEnabled) return world
        return when (gridKind) {
            GridKind.SQUARE -> squareGrid.snapToNearestAnchor(world)
            GridKind.HEX -> hexGrid.snapToNearestAnchor(world)
        }
    }

    fun panBy(delta: Offset) {
        cameraCenter = transform.panBy(
            ScreenVector(delta.x.toDouble(), delta.y.toDouble()),
        ).cameraCenter
        dismissTokenMenu()
        activeTokenManipulation = null
    }

    fun transformBy(pan: Offset, zoomFactor: Float, centroid: Offset) {
        val panned = transform.panBy(ScreenVector(pan.x.toDouble(), pan.y.toDouble()))
        val zoomed = panned.zoomAt(
            anchor = ScreenPoint(centroid.x.toDouble(), centroid.y.toDouble()),
            zoomFactor = zoomFactor.toDouble(),
            minimumPixelsPerWorldUnit = 16.0,
            maximumPixelsPerWorldUnit = 320.0,
        )
        cameraCenter = zoomed.cameraCenter
        pixelsPerWorldUnit = zoomed.pixelsPerWorldUnit
        dismissTokenMenu()
        activeTokenManipulation = null
    }

    fun selectSquareGrid() {
        gridKind = GridKind.SQUARE
    }

    fun selectHexGrid(orientation: HexOrientation) {
        gridKind = GridKind.HEX
        hexOrientation = orientation
    }

    fun selectUnitScale(units: Double): Boolean {
        if (!units.isFinite() || units <= 0.0 || units > 1_000_000.0) return false
        displayedUnitsPerCell = units
        return true
    }

    fun addToken() {
        val id = nextTokenId++
        val color = TokenColorPreset.entries[((id - 1) % TokenColorPreset.entries.size).toInt()]
        val token = TabletopToken(
            id = id,
            name = "Token $id",
            position = snapWorldPoint(cameraCenter),
            widthCells = TokenSizePreset.ONE_BY_ONE.widthCells,
            heightCells = TokenSizePreset.ONE_BY_ONE.heightCells,
            colorArgb = color.argb,
            rotationDegrees = 0.0,
            orientationMarkerAxis = TokenOrientationMarkerAxis.MAJOR,
        )
        tokens.add(token)
        selectedTokenId = id
        tokenMenuVisible = false
        activeTokenManipulation = null
    }

    fun toggleTokenSelection(tokenId: Long) {
        if (tokenById(tokenId) == null) return
        if (selectedTokenId == tokenId) {
            clearTokenSelection()
        } else {
            selectedTokenId = tokenId
            tokenMenuVisible = false
            activeTokenManipulation = null
        }
    }

    fun selectTokenAndOpenMenu(tokenId: Long) {
        if (tokenById(tokenId) == null) return
        selectedTokenId = tokenId
        tokenMenuVisible = true
        activeTokenManipulation = null
    }

    fun beginTokenMove(tokenId: Long) {
        val token = tokenById(tokenId) ?: return
        if (token.movementLocked) return
        selectedTokenId = tokenId
        tokenMenuVisible = false
        activeTokenManipulation = null
    }

    fun moveTokenByScreenDelta(tokenId: Long, delta: Offset) {
        val token = tokenById(tokenId) ?: return
        if (token.movementLocked) return
        updateToken(tokenId) {
            it.copy(
                position = WorldPoint(
                    x = it.position.x + delta.x / pixelsPerWorldUnit,
                    y = it.position.y + delta.y / pixelsPerWorldUnit,
                ),
            )
        }
    }

    fun finishTokenMove(tokenId: Long) {
        val token = tokenById(tokenId) ?: return
        if (token.movementLocked) return
        updateToken(tokenId) {
            it.copy(position = snapWorldPoint(it.position))
        }
    }

    fun beginTokenResize(tokenId: Long) {
        val token = tokenById(tokenId) ?: return
        if (token.scaleLocked) return
        selectedTokenId = tokenId
        tokenMenuVisible = false
        activeTokenManipulation = ActiveTokenManipulation(
            tokenId = tokenId,
            kind = TokenManipulationKind.SCALE,
        )
    }

    fun beginTokenRotation(tokenId: Long) {
        val token = tokenById(tokenId) ?: return
        if (token.rotationLocked) return
        selectedTokenId = tokenId
        tokenMenuVisible = false
        activeTokenManipulation = ActiveTokenManipulation(
            tokenId = tokenId,
            kind = TokenManipulationKind.ROTATION,
        )
    }

    fun finishTokenManipulation(tokenId: Long) {
        if (activeTokenManipulation?.tokenId == tokenId) {
            activeTokenManipulation = null
        }
    }

    fun resizeTokenFromScreenPoint(
        tokenId: Long,
        axis: TokenResizeAxis,
        screenPoint: Offset,
    ) {
        val token = tokenById(tokenId) ?: return
        if (token.scaleLocked) return
        selectedTokenId = tokenId
        tokenMenuVisible = false
        activeTokenManipulation = ActiveTokenManipulation(
            tokenId = tokenId,
            kind = TokenManipulationKind.SCALE,
        )

        val center = worldToScreen(token.position)
        val deltaX = (screenPoint.x - center.x).toDouble()
        val deltaY = (screenPoint.y - center.y).toDouble()
        val radians = Math.toRadians(token.rotationDegrees)
        val localX = deltaX * cos(radians) + deltaY * sin(radians)
        val localY = -deltaX * sin(radians) + deltaY * cos(radians)
        val pixelsPerCell = pixelsPerWorldUnit * cellSizeWorldUnits

        val rawCells = when (axis) {
            TokenResizeAxis.WIDTH -> 2.0 * abs(localX) / pixelsPerCell
            TokenResizeAxis.HEIGHT -> 2.0 * abs(localY) / pixelsPerCell
        }
        val constrainedCells = rawCells.coerceIn(
            TOKEN_HANDLE_MINIMUM_CELLS,
            TOKEN_MAXIMUM_CELLS,
        )
        val adjustedCells = if (snapEnabled) {
            magneticSnapToIncrement(
                value = constrainedCells,
                increment = TOKEN_SCALE_INCREMENT_CELLS,
                threshold = TOKEN_SCALE_MAGNETIC_THRESHOLD_CELLS,
            )
        } else {
            constrainedCells
        }

        updateToken(tokenId) {
            when (axis) {
                TokenResizeAxis.WIDTH -> it.copy(widthCells = adjustedCells)
                TokenResizeAxis.HEIGHT -> it.copy(heightCells = adjustedCells)
            }
        }
    }

    fun rotateTokenFromScreenPoint(tokenId: Long, screenPoint: Offset) {
        val token = tokenById(tokenId) ?: return
        if (token.rotationLocked) return
        selectedTokenId = tokenId
        tokenMenuVisible = false
        activeTokenManipulation = ActiveTokenManipulation(
            tokenId = tokenId,
            kind = TokenManipulationKind.ROTATION,
        )

        val center = worldToScreen(token.position)
        val deltaX = (screenPoint.x - center.x).toDouble()
        val deltaY = (screenPoint.y - center.y).toDouble()
        if (abs(deltaX) < 0.000_001 && abs(deltaY) < 0.000_001) return

        val pointerDegrees = normalizeDegrees(
            Math.toDegrees(atan2(deltaX, -deltaY)),
        )
        val rawRotation = normalizeDegrees(
            pointerDegrees - token.orientationMarkerBaseDegrees,
        )
        val adjustedRotation = if (snapEnabled) {
            magneticSnapToIncrement(
                value = rawRotation,
                increment = TOKEN_ROTATION_INCREMENT_DEGREES,
                threshold = TOKEN_ROTATION_MAGNETIC_THRESHOLD_DEGREES,
            )
        } else {
            rawRotation
        }

        updateToken(tokenId) {
            it.copy(rotationDegrees = normalizeDegrees(adjustedRotation))
        }
    }

    fun isTokenSelected(tokenId: Long): Boolean = selectedTokenId == tokenId

    fun renameSelectedToken(name: String) {
        val tokenId = selectedTokenId ?: return
        updateToken(tokenId) { it.copy(name = name.take(40)) }
    }

    fun toggleSelectedTokenMovementLock() {
        val tokenId = selectedTokenId ?: return
        updateToken(tokenId) { it.copy(movementLocked = !it.movementLocked) }
        activeTokenManipulation = null
    }

    fun toggleSelectedTokenScaleLock() {
        val tokenId = selectedTokenId ?: return
        updateToken(tokenId) { it.copy(scaleLocked = !it.scaleLocked) }
        activeTokenManipulation = null
    }

    fun toggleSelectedTokenRotationLock() {
        val tokenId = selectedTokenId ?: return
        updateToken(tokenId) { it.copy(rotationLocked = !it.rotationLocked) }
        activeTokenManipulation = null
    }

    fun selectSelectedTokenSizePreset(preset: TokenSizePreset) {
        val tokenId = selectedTokenId ?: return
        updateToken(tokenId) {
            it.copy(
                widthCells = preset.widthCells,
                heightCells = preset.heightCells,
            )
        }
    }

    fun applySelectedTokenCustomSize(widthCells: Double, heightCells: Double): Boolean {
        if (!isValidTokenDimension(widthCells) || !isValidTokenDimension(heightCells)) {
            return false
        }
        val tokenId = selectedTokenId ?: return false
        updateToken(tokenId) {
            it.copy(
                widthCells = widthCells,
                heightCells = heightCells,
            )
        }
        return true
    }

    fun selectSelectedTokenColorPreset(preset: TokenColorPreset) {
        val tokenId = selectedTokenId ?: return
        updateToken(tokenId) { it.copy(colorArgb = preset.argb) }
    }

    fun applySelectedTokenCustomColor(hexColor: String): Boolean {
        val color = parseRgbHex(hexColor) ?: return false
        val tokenId = selectedTokenId ?: return false
        updateToken(tokenId) { it.copy(colorArgb = color) }
        return true
    }

    fun selectSelectedTokenRotation(degrees: Double): Boolean {
        if (!degrees.isFinite()) return false
        val tokenId = selectedTokenId ?: return false
        updateToken(tokenId) { it.copy(rotationDegrees = normalizeDegrees(degrees)) }
        return true
    }

    fun selectSelectedTokenMarkerAxis(axis: TokenOrientationMarkerAxis) {
        val tokenId = selectedTokenId ?: return
        updateToken(tokenId) { it.copy(orientationMarkerAxis = axis) }
    }

    fun resetSelectedToken() {
        val tokenId = selectedTokenId ?: return
        updateToken(tokenId) { token ->
            token.copy(position = snapWorldPoint(WorldPoint.Zero))
        }
        tokenMenuVisible = false
        activeTokenManipulation = null
    }

    fun deleteSelectedToken() {
        val tokenId = selectedTokenId ?: return
        tokens.removeAll { it.id == tokenId }
        clearTokenSelection()
    }

    fun clearTokenSelection() {
        selectedTokenId = null
        tokenMenuVisible = false
        activeTokenManipulation = null
    }

    fun dismissTokenMenu() {
        tokenMenuVisible = false
    }

    fun handleMeasurementTap(screenPoint: Offset, markerHitRadiusPx: Float) {
        val path = measurement
        if (path != null) {
            val hitIndex = path.points.indices.minByOrNull { index ->
                worldToScreen(path.points[index]).getDistanceSquared(screenPoint)
            }
            if (hitIndex != null) {
                val markerScreen = worldToScreen(path.points[hitIndex])
                if (markerScreen.getDistanceSquared(screenPoint) <= markerHitRadiusPx * markerHitRadiusPx) {
                    selectedMeasurementMarkerIndex = hitIndex
                    return
                }
            }
        }

        val point = snappedWorldPoint(screenPoint)
        val existing = measurement?.points.orEmpty()
        if (existing.lastOrNull()?.distanceTo(point)?.let { it < 0.000_001 } == true) return
        measurement = MeasurementPath(existing + point)
        selectedMeasurementMarkerIndex = null
        clearTokenSelection()
    }

    fun dismissMeasurementMarkerMenu() {
        selectedMeasurementMarkerIndex = null
    }

    fun deleteMeasurementFromSelectedMarker() {
        val path = measurement ?: return
        val index = selectedMeasurementMarkerIndex ?: return
        if (index !in path.points.indices) {
            selectedMeasurementMarkerIndex = null
            return
        }
        val remaining = path.points.take(index)
        measurement = if (remaining.isEmpty()) null else MeasurementPath(remaining)
        selectedMeasurementMarkerIndex = null
    }

    fun clearMeasurement() {
        measurement = null
        selectedMeasurementMarkerIndex = null
    }

    fun selectDrawingColorPreset(preset: TokenColorPreset) {
        brushColorArgb = preset.argb
        drawingMode = DrawingMode.BRUSH
    }

    fun applyDrawingCustomColor(hexColor: String): Boolean {
        val color = parseRgbHex(hexColor) ?: return false
        brushColorArgb = color
        drawingMode = DrawingMode.BRUSH
        return true
    }

    fun toggleDrawingEraser() {
        drawingMode = if (drawingMode == DrawingMode.ERASER) {
            DrawingMode.BRUSH
        } else {
            DrawingMode.ERASER
        }
        activeStroke = null
    }

    fun beginDrawing(screenPoint: Offset, eraserRadiusPx: Float) {
        clearTokenSelection()
        if (drawingMode == DrawingMode.ERASER) {
            activeStroke = null
            eraseDrawingAt(screenPoint, eraserRadiusPx)
            return
        }
        activeStroke = DrawingStroke(
            points = listOf(screenToWorld(screenPoint)),
            widthWorldUnits = brushWidthWorldUnits,
            colorArgb = brushColorArgb,
        )
    }

    fun continueDrawing(screenPoint: Offset, eraserRadiusPx: Float) {
        if (drawingMode == DrawingMode.ERASER) {
            eraseDrawingAt(screenPoint, eraserRadiusPx)
            return
        }
        val current = activeStroke ?: return
        val point = screenToWorld(screenPoint)
        if (current.points.last().distanceTo(point) < brushWidthWorldUnits / 3.0) return
        activeStroke = current.copy(points = current.points + point)
    }

    fun finishDrawing() {
        if (drawingMode == DrawingMode.BRUSH) {
            activeStroke?.takeIf { it.points.size >= 2 }?.let(strokes::add)
        }
        activeStroke = null
    }

    private fun eraseDrawingAt(screenPoint: Offset, eraserRadiusPx: Float) {
        if (strokes.isEmpty()) return
        val center = screenToWorld(screenPoint)
        val radiusWorld = eraserRadiusPx.toDouble() / pixelsPerWorldUnit
        val rebuilt = buildList {
            strokes.forEach { stroke ->
                var run = mutableListOf<WorldPoint>()
                fun flushRun() {
                    if (run.size >= 2) {
                        add(stroke.copy(points = run.toList()))
                    }
                    run = mutableListOf()
                }

                stroke.points.forEach { point ->
                    if (point.distanceTo(center) <= radiusWorld) {
                        flushRun()
                    } else {
                        run.add(point)
                    }
                }
                flushRun()
            }
        }
        strokes.clear()
        strokes.addAll(rebuilt)
    }

    fun clearDrawings() {
        strokes.clear()
        activeStroke = null
    }

    fun addNoteAtScreenPoint(screenPoint: Offset) {
        val id = nextNoteId++
        notes.add(
            TabletopNote(
                id = id,
                position = snappedWorldPoint(screenPoint),
                text = "",
            ),
        )
        clearTokenSelection()
    }

    fun updateNoteText(noteId: Long, text: String) {
        updateNote(noteId) { it.copy(text = text.take(MAX_NOTE_TEXT_LENGTH)) }
    }

    fun moveNoteByScreenDelta(noteId: Long, delta: Offset) {
        updateNote(noteId) { note ->
            note.copy(
                position = WorldPoint(
                    x = note.position.x + delta.x / pixelsPerWorldUnit,
                    y = note.position.y + delta.y / pixelsPerWorldUnit,
                ),
            )
        }
    }

    fun finishNoteMove(noteId: Long) {
        updateNote(noteId) { it.copy(position = snapWorldPoint(it.position)) }
    }

    fun deleteNote(noteId: Long) {
        notes.removeAll { it.id == noteId }
    }

    internal fun createPersistentSnapshot(): TabletopSceneSnapshot =
        TabletopSceneSnapshot(
            gridKind = gridKind,
            hexOrientation = hexOrientation,
            snapEnabled = snapEnabled,
            cameraCenter = cameraCenter,
            pixelsPerWorldUnit = pixelsPerWorldUnit,
            displayedUnitsPerCell = displayedUnitsPerCell,
            tokens = tokens.toList(),
            measurement = measurement,
            strokes = strokes.toList(),
            brushColorArgb = brushColorArgb,
            notes = notes.toList(),
        )

    internal fun restorePersistentSnapshot(snapshot: TabletopSceneSnapshot) {
        gridKind = snapshot.gridKind
        hexOrientation = snapshot.hexOrientation
        snapEnabled = snapshot.snapEnabled
        cameraCenter = snapshot.cameraCenter
        pixelsPerWorldUnit = snapshot.pixelsPerWorldUnit.coerceIn(16.0, 320.0)
        displayedUnitsPerCell = snapshot.displayedUnitsPerCell

        tokens.clear()
        tokens.addAll(snapshot.tokens)
        nextTokenId = (snapshot.tokens.maxOfOrNull { it.id } ?: 0L) + 1L

        measurement = snapshot.measurement
        selectedMeasurementMarkerIndex = null
        strokes.clear()
        strokes.addAll(snapshot.strokes)
        activeStroke = null
        drawingMode = DrawingMode.BRUSH
        brushColorArgb = snapshot.brushColorArgb

        notes.clear()
        notes.addAll(snapshot.notes)
        nextNoteId = (snapshot.notes.maxOfOrNull { it.id } ?: 0L) + 1L

        clearTokenSelection()
    }

    private fun tokenById(tokenId: Long): TabletopToken? =
        tokens.firstOrNull { it.id == tokenId }

    private fun updateToken(
        tokenId: Long,
        transform: (TabletopToken) -> TabletopToken,
    ) {
        val index = tokens.indexOfFirst { it.id == tokenId }
        if (index < 0) return
        tokens[index] = transform(tokens[index])
    }

    private fun updateNote(
        noteId: Long,
        transform: (TabletopNote) -> TabletopNote,
    ) {
        val index = notes.indexOfFirst { it.id == noteId }
        if (index < 0) return
        notes[index] = transform(notes[index])
    }

    private fun isValidTokenDimension(value: Double): Boolean =
        value.isFinite() && value in 0.1..TOKEN_MAXIMUM_CELLS

    private fun parseRgbHex(input: String): Long? {
        val normalized = input.trim().removePrefix("#")
        if (normalized.length != 6) return null
        val rgb = normalized.toLongOrNull(radix = 16) ?: return null
        return 0xFF000000L or rgb
    }

    private fun normalizeDegrees(degrees: Double): Double {
        val normalized = degrees % 360.0
        return if (normalized < 0.0) normalized + 360.0 else normalized
    }

    private fun magneticSnapToIncrement(
        value: Double,
        increment: Double,
        threshold: Double,
    ): Double {
        val snapTarget = snapToIncrement(value, increment)
        return if (abs(value - snapTarget) <= threshold) snapTarget else value
    }

    private fun snapToIncrement(value: Double, increment: Double): Double =
        floor(value / increment + 0.5) * increment

    private companion object {
        const val TOKEN_ROTATION_INCREMENT_DEGREES = 15.0
        const val TOKEN_ROTATION_MAGNETIC_THRESHOLD_DEGREES = 3.0
        const val TOKEN_SCALE_INCREMENT_CELLS = 0.5
        const val TOKEN_SCALE_MAGNETIC_THRESHOLD_CELLS = 0.1
        const val TOKEN_HANDLE_MINIMUM_CELLS = 0.5
        const val TOKEN_MAXIMUM_CELLS = 100.0
        const val MAX_NOTE_TEXT_LENGTH = 5_000
    }
}

private fun Offset.getDistanceSquared(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return dx * dx + dy * dy
}
