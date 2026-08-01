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
    val tokenDiameterWorldUnits: Double = cellSizeWorldUnits
    val brushWidthWorldUnits: Double = 0.065

    private val unitScalePresets = listOf(1.0, 5.0, 10.0)
    private var unitScaleIndex by mutableStateOf(1)
    val displayedUnitsPerCell: Double get() = unitScalePresets[unitScaleIndex]
    val unitScale: UnitScale
        get() = UnitScale(displayedUnitsPerCell / cellSizeWorldUnits, "ft")

    var tokenPosition by mutableStateOf(WorldPoint(0.0, 0.0))
        private set
    var tokenSelected by mutableStateOf(false)
        private set
    var tokenMenuVisible by mutableStateOf(false)
        private set

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

    fun snappedWorldPoint(screenPoint: Offset): WorldPoint {
        val world = screenToWorld(screenPoint)
        if (!snapEnabled) return world
        return when (gridKind) {
            GridKind.SQUARE -> squareGrid.snapToCellCenter(world)
            GridKind.HEX -> hexGrid.snapToCenter(world)
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

    fun selectTokenAndOpenMenu() {
        tokenSelected = true
        tokenMenuVisible = true
    }

    fun beginTokenMove() {
        tokenSelected = true
        tokenMenuVisible = false
    }

    fun moveTokenByScreenDelta(delta: Offset) {
        tokenPosition = WorldPoint(
            x = tokenPosition.x + delta.x / pixelsPerWorldUnit,
            y = tokenPosition.y + delta.y / pixelsPerWorldUnit,
        )
    }

    fun finishTokenMove() {
        if (snapEnabled) {
            tokenPosition = when (gridKind) {
                GridKind.SQUARE -> squareGrid.snapToCellCenter(tokenPosition)
                GridKind.HEX -> hexGrid.snapToCenter(tokenPosition)
            }
        }
    }

    fun clearTokenSelection() {
        tokenSelected = false
        tokenMenuVisible = false
    }

    fun dismissTokenMenu() {
        tokenMenuVisible = false
    }

    fun resetToken() {
        tokenPosition = when (gridKind) {
            GridKind.SQUARE -> squareGrid.snapToCellCenter(WorldPoint.Zero)
            GridKind.HEX -> hexGrid.snapToCenter(WorldPoint.Zero)
        }
        tokenSelected = true
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
}
