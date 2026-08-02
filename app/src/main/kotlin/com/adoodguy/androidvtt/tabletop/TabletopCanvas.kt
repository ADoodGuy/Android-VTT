package com.adoodguy.androidvtt.tabletop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.adoodguy.androidvtt.geometry.GridKind
import com.adoodguy.androidvtt.geometry.MeasurementEngine
import com.adoodguy.androidvtt.geometry.WorldPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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

        state.tokens.forEach { token ->
            TokenView(
                state = state,
                token = token,
            )
        }

        state.selectedToken?.let { token ->
            SelectedTokenControls(
                state = state,
                token = token,
            )
        }
    }
}

@Composable
private fun TokenView(
    state: TabletopState,
    token: TabletopToken,
) {
    val density = LocalDensity.current
    val center = state.worldToScreen(token.position)
    val widthPx = (
        token.widthCells *
            state.cellSizeWorldUnits *
            state.pixelsPerWorldUnit
        ).toFloat()
    val heightPx = (
        token.heightCells *
            state.cellSizeWorldUnits *
            state.pixelsPerWorldUnit
        ).toFloat()

    val minimumVisualDimensionPx = with(density) { 20.dp.toPx() }
    val minimumTouchDimensionPx = with(density) { 48.dp.toPx() }
    val renderedWidthPx = maxOf(widthPx, minimumVisualDimensionPx)
    val renderedHeightPx = maxOf(heightPx, minimumVisualDimensionPx)

    // Use the rotated ellipse's bounding box so the full visual remains
    // touchable without reserving an unnecessarily large square target.
    val radians = Math.toRadians(token.rotationDegrees)
    val absoluteCosine = abs(cos(radians)).toFloat()
    val absoluteSine = abs(sin(radians)).toFloat()
    val rotatedWidthPx =
        renderedWidthPx * absoluteCosine + renderedHeightPx * absoluteSine
    val rotatedHeightPx =
        renderedWidthPx * absoluteSine + renderedHeightPx * absoluteCosine
    val containerWidthPx = maxOf(rotatedWidthPx, minimumTouchDimensionPx)
    val containerHeightPx = maxOf(rotatedHeightPx, minimumTouchDimensionPx)
    val containerWidthDp = with(density) { containerWidthPx.toDp() }
    val containerHeightDp = with(density) { containerHeightPx.toDp() }
    val selected = state.isTokenSelected(token.id)

    Box(
        modifier = Modifier
            .zIndex(if (selected) 1f else 0f)
            .offsetInPixels(
                x = center.x - containerWidthPx / 2f,
                y = center.y - containerHeightPx / 2f,
            )
            .size(containerWidthDp, containerHeightDp)
            .pointerInput(token.id) {
                detectTapGestures(
                    onTap = { state.selectToken(token.id) },
                    onDoubleTap = { state.selectTokenAndOpenMenu(token.id) },
                )
            }
            .pointerInput(
                token.id,
                state.pixelsPerWorldUnit,
                state.gridKind,
                state.hexOrientation,
                state.snapEnabled,
            ) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { state.beginTokenMove(token.id) },
                    onDragEnd = { state.finishTokenMove(token.id) },
                    onDragCancel = { state.finishTokenMove(token.id) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        state.moveTokenByScreenDelta(token.id, dragAmount)
                    },
                )
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawToken(
                token = token,
                renderedWidthPx = renderedWidthPx,
                renderedHeightPx = renderedHeightPx,
                selected = selected,
            )
        }
    }
}

@Composable
private fun BoxScope.SelectedTokenControls(
    state: TabletopState,
    token: TabletopToken,
) {
    val density = LocalDensity.current
    val center = state.worldToScreen(token.position)
    val minimumVisualDimensionPx = with(density) { 20.dp.toPx() }
    val renderedWidthPx = maxOf(
        (
            token.widthCells *
                state.cellSizeWorldUnits *
                state.pixelsPerWorldUnit
            ).toFloat(),
        minimumVisualDimensionPx,
    )
    val renderedHeightPx = maxOf(
        (
            token.heightCells *
                state.cellSizeWorldUnits *
                state.pixelsPerWorldUnit
            ).toFloat(),
        minimumVisualDimensionPx,
    )

    val topPoint = pointAtClockwiseDegrees(
        center = center,
        degrees = token.rotationDegrees,
        distance = renderedHeightPx / 2f,
    )
    val rightPoint = pointAtClockwiseDegrees(
        center = center,
        degrees = token.rotationDegrees + 90.0,
        distance = renderedWidthPx / 2f,
    )
    val bottomPoint = pointAtClockwiseDegrees(
        center = center,
        degrees = token.rotationDegrees + 180.0,
        distance = renderedHeightPx / 2f,
    )
    val leftPoint = pointAtClockwiseDegrees(
        center = center,
        degrees = token.rotationDegrees + 270.0,
        distance = renderedWidthPx / 2f,
    )

    val markerBaseDegrees = token.orientationMarkerBaseDegrees
    val markerEdgeRadius = orientationAxisRadius(
        renderedWidthPx = renderedWidthPx,
        renderedHeightPx = renderedHeightPx,
        baseDegrees = markerBaseDegrees,
    )
    val markerRadius = (markerEdgeRadius - with(density) { 3.dp.toPx() }).coerceAtLeast(0f)
    val markerDegrees = token.rotationDegrees + markerBaseDegrees
    val markerEndpoint = pointAtClockwiseDegrees(
        center = center,
        degrees = markerDegrees,
        distance = markerRadius,
    )
    val rotationHandleGapPx = with(density) { 28.dp.toPx() }
    val rotationHandlePoint = pointAtClockwiseDegrees(
        center = center,
        degrees = markerDegrees,
        distance = markerRadius + rotationHandleGapPx,
    )

    Canvas(
        modifier = Modifier
            .matchParentSize()
            .zIndex(2f),
    ) {
        drawLine(
            color = Color(0xCC20343F),
            start = markerEndpoint,
            end = rotationHandlePoint,
            strokeWidth = 2f * density,
        )
    }

    TokenControlHandle(
        screenPosition = topPoint,
        controlKey = "${token.id}-height-top",
        style = TokenControlHandleStyle.SCALE,
        onDragTo = {
            state.resizeTokenFromScreenPoint(
                tokenId = token.id,
                axis = TokenResizeAxis.HEIGHT,
                screenPoint = it,
            )
        },
    )
    TokenControlHandle(
        screenPosition = rightPoint,
        controlKey = "${token.id}-width-right",
        style = TokenControlHandleStyle.SCALE,
        onDragTo = {
            state.resizeTokenFromScreenPoint(
                tokenId = token.id,
                axis = TokenResizeAxis.WIDTH,
                screenPoint = it,
            )
        },
    )
    TokenControlHandle(
        screenPosition = bottomPoint,
        controlKey = "${token.id}-height-bottom",
        style = TokenControlHandleStyle.SCALE,
        onDragTo = {
            state.resizeTokenFromScreenPoint(
                tokenId = token.id,
                axis = TokenResizeAxis.HEIGHT,
                screenPoint = it,
            )
        },
    )
    TokenControlHandle(
        screenPosition = leftPoint,
        controlKey = "${token.id}-width-left",
        style = TokenControlHandleStyle.SCALE,
        onDragTo = {
            state.resizeTokenFromScreenPoint(
                tokenId = token.id,
                axis = TokenResizeAxis.WIDTH,
                screenPoint = it,
            )
        },
    )
    TokenControlHandle(
        screenPosition = rotationHandlePoint,
        controlKey = "${token.id}-rotation",
        style = TokenControlHandleStyle.ROTATE,
        onDragTo = {
            state.rotateTokenFromScreenPoint(
                tokenId = token.id,
                screenPoint = it,
            )
        },
    )
}

@Composable
private fun BoxScope.TokenControlHandle(
    screenPosition: Offset,
    controlKey: String,
    style: TokenControlHandleStyle,
    onDragTo: (Offset) -> Unit,
) {
    val density = LocalDensity.current
    val touchTargetDp = 24.dp
    val touchTargetPx = with(density) { touchTargetDp.toPx() }
    val currentScreenPosition = rememberUpdatedState(screenPosition)
    val currentOnDragTo = rememberUpdatedState(onDragTo)

    Box(
        modifier = Modifier
            .zIndex(3f)
            .offsetInPixels(
                x = screenPosition.x - touchTargetPx / 2f,
                y = screenPosition.y - touchTargetPx / 2f,
            )
            .size(touchTargetDp)
            .pointerInput(controlKey) {
                var pointerScreenPosition = Offset.Zero
                detectDragGestures(
                    onDragStart = { localStart ->
                        val touchCenter = Offset(touchTargetPx / 2f, touchTargetPx / 2f)
                        pointerScreenPosition =
                            currentScreenPosition.value + (localStart - touchCenter)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        pointerScreenPosition += dragAmount
                        currentOnDragTo.value(pointerScreenPosition)
                    },
                )
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val visualSize = 9.dp.toPx()
            val visualCenter = center
            when (style) {
                TokenControlHandleStyle.SCALE -> {
                    val topLeft = Offset(
                        visualCenter.x - visualSize / 2f,
                        visualCenter.y - visualSize / 2f,
                    )
                    drawRect(
                        color = Color.White,
                        topLeft = topLeft,
                        size = Size(visualSize, visualSize),
                    )
                    drawRect(
                        color = Color(0xFFFF9800),
                        topLeft = topLeft,
                        size = Size(visualSize, visualSize),
                        style = Stroke(width = 2f * density),
                    )
                }

                TokenControlHandleStyle.ROTATE -> {
                    drawCircle(
                        color = Color(0xFF6A4C93),
                        radius = visualSize / 2f,
                        center = visualCenter,
                    )
                    drawCircle(
                        color = Color.White,
                        radius = visualSize / 2f,
                        center = visualCenter,
                        style = Stroke(width = 2f * density),
                    )
                }
            }
        }
    }
}

private enum class TokenControlHandleStyle {
    SCALE,
    ROTATE,
}

private fun DrawScope.drawToken(
    token: TabletopToken,
    renderedWidthPx: Float,
    renderedHeightPx: Float,
    selected: Boolean,
) {
    val tokenCenter = center
    val inset = 2f
    val ovalSize = Size(
        width = (renderedWidthPx - inset * 2f).coerceAtLeast(0f),
        height = (renderedHeightPx - inset * 2f).coerceAtLeast(0f),
    )
    val ovalTopLeft = Offset(
        x = tokenCenter.x - ovalSize.width / 2f,
        y = tokenCenter.y - ovalSize.height / 2f,
    )
    val outlineColor = if (selected) Color(0xFFFFB300) else Color(0xFF20343F)
    val outlineWidth = if (selected) 6f else 3f

    rotate(
        degrees = token.rotationDegrees.toFloat(),
        pivot = tokenCenter,
    ) {
        drawOval(
            color = Color(token.colorArgb),
            topLeft = ovalTopLeft,
            size = ovalSize,
        )

        val markerRadius = (
            orientationAxisRadius(
                renderedWidthPx = ovalSize.width,
                renderedHeightPx = ovalSize.height,
                baseDegrees = token.orientationMarkerBaseDegrees,
            ) - 3f * density
            ).coerceAtLeast(0f)
        val markerEnd = pointAtClockwiseDegrees(
            center = tokenCenter,
            degrees = token.orientationMarkerBaseDegrees,
            distance = markerRadius,
        )
        drawLine(
            color = Color(0xAA000000),
            start = tokenCenter,
            end = markerEnd,
            strokeWidth = 5f * density,
        )
        drawLine(
            color = Color(0xEEFFFFFF),
            start = tokenCenter,
            end = markerEnd,
            strokeWidth = 2f * density,
        )

        drawOval(
            color = outlineColor,
            topLeft = ovalTopLeft,
            size = ovalSize,
            style = Stroke(width = outlineWidth),
        )
    }
}

private fun orientationAxisRadius(
    renderedWidthPx: Float,
    renderedHeightPx: Float,
    baseDegrees: Double,
): Float =
    if (abs(normalizeDegreesForGeometry(baseDegrees) - 90.0) < 0.000_001) {
        renderedWidthPx / 2f
    } else {
        renderedHeightPx / 2f
    }

private fun pointAtClockwiseDegrees(
    center: Offset,
    degrees: Double,
    distance: Float,
): Offset {
    val radians = Math.toRadians(degrees)
    return Offset(
        x = center.x + sin(radians).toFloat() * distance,
        y = center.y - cos(radians).toFloat() * distance,
    )
}

private fun normalizeDegreesForGeometry(degrees: Double): Double {
    val normalized = degrees % 360.0
    return if (normalized < 0.0) normalized + 360.0 else normalized
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
