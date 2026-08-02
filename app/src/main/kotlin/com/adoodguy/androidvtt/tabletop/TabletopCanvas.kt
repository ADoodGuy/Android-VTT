package com.adoodguy.androidvtt.tabletop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.adoodguy.androidvtt.geometry.GridKind
import com.adoodguy.androidvtt.geometry.MeasurementEngine
import com.adoodguy.androidvtt.geometry.WorldPoint
import kotlin.math.roundToInt

@Composable
fun TabletopCanvas(
    state: TabletopState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .tabletopGestures(state),
        ) {
            drawRect(Color(0xFFF5F2E9))
            drawGrid(state)
            state.strokes.forEach { drawStroke(it, state) }
            state.activeStroke?.let { drawStroke(it, state) }
            state.measurement?.let { drawMeasurement(it, state) }
            drawOrigin(state)
        }

        PrototypeToken(state)
    }
}

@Composable
private fun PrototypeToken(state: TabletopState) {
    val density = LocalDensity.current
    val center = state.worldToScreen(state.tokenPosition)
    val diameterPx = (state.tokenDiameterWorldUnits * state.pixelsPerWorldUnit).toFloat()
    val diameterDp = with(density) { diameterPx.toDp() }

    val minimumDiameterPx = with(density) { 20.dp.toPx() }
    val renderedDiameterPx = maxOf(diameterPx, minimumDiameterPx)

    Box(
        modifier = Modifier
            // Position the entire interactive token before attaching pointer input.
            // Modifier order matters: placing offset after pointerInput moves only the
            // drawing while leaving the touch target at the original top-left position.
            .offsetInPixels(
                x = center.x - renderedDiameterPx / 2f,
                y = center.y - renderedDiameterPx / 2f,
            )
            .size(diameterDp.coerceAtLeast(20.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { state.selectTokenAndOpenMenu() },
                )
            }
            .pointerInput(
                state.pixelsPerWorldUnit,
                state.gridKind,
                state.hexOrientation,
                state.snapEnabled,
            ) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { state.beginTokenMove() },
                    onDragEnd = { state.finishTokenMove() },
                    onDragCancel = { state.finishTokenMove() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        state.moveTokenByScreenDelta(dragAmount)
                    },
                )
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                color = Color(0xFF4E6E81),
                radius = minOf(size.width, size.height) / 2f - 2f,
            )
            drawCircle(
                color = if (state.tokenSelected) Color(0xFFFFB300) else Color(0xFF20343F),
                radius = minOf(size.width, size.height) / 2f - 2f,
                style = Stroke(width = if (state.tokenSelected) 6f else 3f),
            )
        }
    }
}

private fun Modifier.offsetInPixels(x: Float, y: Float): Modifier =
    this.then(
        Modifier.offset {
            IntOffset(x.roundToInt(), y.roundToInt())
        },
    )

private fun DrawScope.drawGrid(state: TabletopState) {
    val transform = state.transform
    val visibleBounds = transform.visibleWorldRect()
    val gridColor = Color(0x553E4A52)
    val axisColor = Color(0x884E6E81)
    val gridStroke = (1.15f * density).coerceAtLeast(1f)

    when (state.gridKind) {
        GridKind.SQUARE -> {
            state.squareGrid.verticalLines(visibleBounds).forEach { x ->
                val screenX = state.worldToScreen(WorldPoint(x, 0.0)).x
                drawLine(gridColor, Offset(screenX, 0f), Offset(screenX, size.height), gridStroke)
            }
            state.squareGrid.horizontalLines(visibleBounds).forEach { y ->
                val screenY = state.worldToScreen(WorldPoint(0.0, y)).y
                drawLine(gridColor, Offset(0f, screenY), Offset(size.width, screenY), gridStroke)
            }
        }

        GridKind.HEX -> {
            state.hexGrid.visibleCells(visibleBounds).forEach { coordinate ->
                val corners = state.hexGrid.corners(state.hexGrid.centerOf(coordinate))
                val path = Path()
                corners.forEachIndexed { index, point ->
                    val screen = state.worldToScreen(point)
                    if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
                }
                path.close()
                drawPath(path, gridColor, style = Stroke(gridStroke))
            }
        }
    }

    val origin = state.worldToScreen(WorldPoint.Zero)
    drawLine(axisColor, Offset(origin.x, 0f), Offset(origin.x, size.height), gridStroke * 1.5f)
    drawLine(axisColor, Offset(0f, origin.y), Offset(size.width, origin.y), gridStroke * 1.5f)
}

private fun DrawScope.drawOrigin(state: TabletopState) {
    val origin = state.worldToScreen(WorldPoint.Zero)
    drawCircle(Color(0xFFB53A3A), radius = 5f * density, center = origin)
}

private fun DrawScope.drawStroke(stroke: DrawingStroke, state: TabletopState) {
    if (stroke.points.size < 2) return
    val path = Path()
    stroke.points.forEachIndexed { index, point ->
        val screen = state.worldToScreen(point)
        if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
    }
    drawPath(
        path = path,
        color = Color(0xFF9C3D54),
        style = Stroke(
            width = (stroke.widthWorldUnits * state.pixelsPerWorldUnit).toFloat().coerceAtLeast(1f),
        ),
    )
}

private fun DrawScope.drawMeasurement(line: MeasurementLine, state: TabletopState) {
    val start = state.worldToScreen(line.start)
    val end = state.worldToScreen(line.end)
    drawLine(
        color = Color(0xFFD35400),
        start = start,
        end = end,
        strokeWidth = 4f * density,
    )
    drawCircle(Color(0xFFD35400), radius = 5f * density, center = start)
    drawCircle(Color(0xFFD35400), radius = 5f * density, center = end)
}

fun measurementText(state: TabletopState): String? {
    val line = state.measurement ?: return null
    val result = when (state.gridKind) {
        GridKind.SQUARE -> MeasurementEngine.measureSquare(
            line.start,
            line.end,
            state.squareGrid,
            state.unitScale,
        )

        GridKind.HEX -> MeasurementEngine.measureHex(
            line.start,
            line.end,
            state.hexGrid,
            state.unitScale,
        )
    }
    return result.formatted()
}
