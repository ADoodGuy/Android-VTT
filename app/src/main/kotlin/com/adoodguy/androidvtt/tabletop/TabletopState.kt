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

    val selectedToken: TabletopToken?
        get() = selectedTokenId?.let(::tokenById)

    var measurement by mutableStateOf<MeasurementLine?>(null)
        private set

    val strokes = mutableStateListOf<DrawingStroke>()
    var activeStroke by mutableStateOf<DrawingStroke?>(null)
        private set

    val squareGrid: SquareGridGeometry
        get() = SquareGridGeometry(cellSizeWorldUnits)
    val hexGrid: HexGridGeometry
        get() = HexGridGeometry(cellSizeWorldUnits, hexOrientation)

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
    }

    fun selectToken(tokenId: Long) {
        if (tokenById(tokenId) == null) return
        selectedTokenId = tokenId
        tokenMenuVisible = false
    }

    fun selectTokenAndOpenMenu(tokenId: Long) {
        if (tokenById(tokenId) == null) return
        selectedTokenId = tokenId
        tokenMenuVisible = true
    }

    fun beginTokenMove(tokenId: Long) {
        if (tokenById(tokenId) == null) return
        selectedTokenId = tokenId
        tokenMenuVisible = false
    }

    fun moveTokenByScreenDelta(tokenId: Long, delta: Offset) {
        updateToken(tokenId) { token ->
            token.copy(
                position = WorldPoint(
                    x = token.position.x + delta.x / pixelsPerWorldUnit,
                    y = token.position.y + delta.y / pixelsPerWorldUnit,
                ),
            )
        }
    }

    fun finishTokenMove(tokenId: Long) {
        updateToken(tokenId) { token ->
            token.copy(position = snapWorldPoint(token.position))
        }
    }

    fun resizeTokenFromScreenPoint(
        tokenId: Long,
        axis: TokenResizeAxis,
        screenPoint: Offset,
    ) {
        val token = tokenById(tokenId) ?: return
        selectedTokenId = tokenId
        tokenMenuVisible = false

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
        val snappedCells = snapToIncrement(rawCells, TOKEN_SCALE_INCREMENT_CELLS)
            .coerceIn(TOKEN_HANDLE_MINIMUM_CELLS, TOKEN_MAXIMUM_CELLS)

        updateToken(tokenId) {
            when (axis) {
                TokenResizeAxis.WIDTH -> it.copy(widthCells = snappedCells)
                TokenResizeAxis.HEIGHT -> it.copy(heightCells = snappedCells)
            }
        }
    }

    fun rotateTokenFromScreenPoint(tokenId: Long, screenPoint: Offset) {
        val token = tokenById(tokenId) ?: return
        selectedTokenId = tokenId
        tokenMenuVisible = false

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
        val snappedRotation = snapToIncrement(
            rawRotation,
            TOKEN_ROTATION_INCREMENT_DEGREES,
        )
        updateToken(tokenId) {
            it.copy(rotationDegrees = normalizeDegrees(snappedRotation))
        }
    }

    fun isTokenSelected(tokenId: Long): Boolean = selectedTokenId == tokenId

    fun renameSelectedToken(name: String) {
        val tokenId = selectedTokenId ?: return
        updateToken(tokenId) { it.copy(name = name.take(40)) }
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
    }

    fun deleteSelectedToken() {
        val tokenId = selectedTokenId ?: return
        tokens.removeAll { it.id == tokenId }
        clearTokenSelection()
    }

    fun clearTokenSelection() {
        selectedTokenId = null
        tokenMenuVisible = false
    }

    fun dismissTokenMenu() {
        tokenMenuVisible = false
    }

    fun beginMeasurement(screenPoint: Offset) {
        val point = snappedWorldPoint(screenPoint)
        measurement = MeasurementLine(point, point)
        clearTokenSelection()
    }

    fun updateMeasurement(screenPoint: Offset) {
        val current = measurement ?: return
        measurement = current.copy(end = snappedWorldPoint(screenPoint))
    }

    fun clearMeasurement() {
        measurement = null
    }

    fun beginStroke(screenPoint: Offset) {
        activeStroke = DrawingStroke(
            points = listOf(screenToWorld(screenPoint)),
            widthWorldUnits = brushWidthWorldUnits,
        )
        clearTokenSelection()
    }

    fun appendStrokePoint(screenPoint: Offset) {
        val current = activeStroke ?: return
        val point = screenToWorld(screenPoint)
        if (current.points.last().distanceTo(point) < brushWidthWorldUnits / 3.0) return
        activeStroke = current.copy(points = current.points + point)
    }

    fun finishStroke() {
        activeStroke?.takeIf { it.points.size >= 2 }?.let(strokes::add)
        activeStroke = null
    }

    fun clearDrawings() {
        strokes.clear()
        activeStroke = null
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

    private fun snapToIncrement(value: Double, increment: Double): Double =
        floor(value / increment + 0.5) * increment

    private companion object {
        const val TOKEN_ROTATION_INCREMENT_DEGREES = 15.0
        const val TOKEN_SCALE_INCREMENT_CELLS = 0.5
        const val TOKEN_HANDLE_MINIMUM_CELLS = 0.5
        const val TOKEN_MAXIMUM_CELLS = 100.0
    }
}
