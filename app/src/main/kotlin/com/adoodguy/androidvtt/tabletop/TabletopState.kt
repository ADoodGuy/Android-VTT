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

    private val unitScalePresets = listOf(1.0, 5.0, 10.0)
    private var unitScaleIndex by mutableStateOf(1)
    val displayedUnitsPerCell: Double get() = unitScalePresets[unitScaleIndex]
    val unitScale: UnitScale
        get() = UnitScale(displayedUnitsPerCell / cellSizeWorldUnits, "ft")

    private val tokenSizePresetsInCells = listOf(0.5, 1.0, 2.0)
    private var nextTokenId = 2L

    val tokens = mutableStateListOf(
        TabletopToken(
            id = 1L,
            name = "Token 1",
            position = WorldPoint.Zero,
            diameterWorldUnits = cellSizeWorldUnits,
            color = TokenColor.BLUE,
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

    fun cycleUnitScale() {
        unitScaleIndex = (unitScaleIndex + 1) % unitScalePresets.size
    }

    fun addToken() {
        val id = nextTokenId++
        val token = TabletopToken(
            id = id,
            name = "Token $id",
            position = snapWorldPoint(cameraCenter),
            diameterWorldUnits = cellSizeWorldUnits,
            color = TokenColor.entries[((id - 1) % TokenColor.entries.size).toInt()],
        )
        tokens.add(token)
        selectedTokenId = id
        tokenMenuVisible = true
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

    fun isTokenSelected(tokenId: Long): Boolean = selectedTokenId == tokenId

    fun renameSelectedToken(name: String) {
        val tokenId = selectedTokenId ?: return
        updateToken(tokenId) { it.copy(name = name.take(40)) }
    }

    fun cycleSelectedTokenSize() {
        val tokenId = selectedTokenId ?: return
        updateToken(tokenId) { token ->
            val sizeInCells = token.diameterWorldUnits / cellSizeWorldUnits
            val currentIndex = tokenSizePresetsInCells.indexOfFirst {
                abs(it - sizeInCells) < 0.000_001
            }.takeIf { it >= 0 } ?: 1
            val nextSize = tokenSizePresetsInCells[(currentIndex + 1) % tokenSizePresetsInCells.size]
            token.copy(diameterWorldUnits = nextSize * cellSizeWorldUnits)
        }
    }

    fun cycleSelectedTokenColor() {
        val tokenId = selectedTokenId ?: return
        updateToken(tokenId) { token ->
            val nextIndex = (token.color.ordinal + 1) % TokenColor.entries.size
            token.copy(color = TokenColor.entries[nextIndex])
        }
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
}
