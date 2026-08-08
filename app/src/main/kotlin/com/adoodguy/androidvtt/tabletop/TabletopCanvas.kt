package com.adoodguy.androidvtt.tabletop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
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
    val mapConfiguration = TabletopMapStore.configuration
    val mapImage = rememberTabletopMapImage(mapConfiguration.imageUri)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .tabletopGestures(state),
        ) {
            drawRect(Color(0xFFF5F2E9))
            drawTabletopMap(mapImage, mapConfiguration, state)
            drawGrid(state)
            state.strokes.forEach { drawStroke(it, state) }
            state.activeStroke?.let { drawStroke(it, state) }
            state.measurement?.let { drawMeasurement(it, state) }
            drawOrigin(state)
        }

        state.tokens.forEach { token ->
            TokenView(state = state, token = token)
        }

        state.selectedToken?.let { token ->
            SelectedTokenControls(state = state, token = token)
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
        token.widthCells * state.cellSizeWorldUnits * state.pixelsPerWorldUnit
        ).toFloat()
    val heightPx = (
        token.heightCells * state.cellSizeWorldUnits * state.pixelsPerWorldUnit
        ).toFloat()

    val minimumVisualDimensionPx = with(density) { 20.dp.toPx() }
    val minimumTouchDimensionPx = with(density) { 48.dp.toPx() }
    val renderedWidthPx = maxOf(widthPx, minimumVisualDimensionPx)
    val renderedHeightPx = maxOf(heightPx, minimumVisualDimensionPx)

    val radians = Math.toRadians(token.rotationDegrees)
    val absoluteCosine = abs(cos(radians)).toFloat()
    val absoluteSine = abs(sin(radians)).toFloat()
    val rotatedWidthPx = renderedWidthPx * absoluteCosine + renderedHeightPx * absoluteSine
    val rotatedHeightPx = renderedWidthPx * absoluteSine + renderedHeightPx * absoluteCosine
    val containerWidthPx = maxOf(rotatedWidthPx, minimumTouchDimensionPx)
    val containerHeightPx = maxOf(rotatedHeightPx, minimumTouchDimensionPx)
    val selected = state.isTokenSelected(token.id)

    Box(
        modifier = Modifier
            .zIndex(if (selected) 1f else 0f)
            .offsetInPixels(
                x = center.x - containerWidthPx / 2f,
                y = center.y - containerHeightPx / 2f,
            )
            .size(
                with(density) { containerWidthPx.toDp() },
                with(density) { containerHeightPx.toDp() },
            )
            .pointerInput(token.id) {
                detectTapGestures(
                    onTap = { state.toggleTokenSelection(token.id) },
                    onDoubleTap = { state.clearTokenSelection() },
                    onLongPress = { state.selectTokenAndOpenMenu(token.id) },
                )
            }
            .pointerInput(
                token.id,
                state.pixelsPerWorldUnit,
                state.gridKind,
                state.hexOrientation,
                state.snapEnabled,
                token.movementLocked,
            ) {
                detectDragGestures(
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
        (token.widthCells * state.cellSizeWorldUnits * state.pixelsPerWorldUnit).toFloat(),
        minimumVisualDimensionPx,
    )
    val renderedHeightPx = maxOf(
        (token.heightCells * state.cellSizeWorldUnits * state.pixelsPerWorldUnit).toFloat(),
        minimumVisualDimensionPx,
    )

    val topPoint = pointAtClockwiseDegrees(
        center,
        token.rotationDegrees,
        renderedHeightPx / 2f,
    )
    val rightPoint = pointAtClockwiseDegrees(
        center,
        token.rotationDegrees + 90.0,
        renderedWidthPx / 2f,
    )
    val bottomPoint = pointAtClockwiseDegrees(
        center,
        token.rotationDegrees + 180.0,
        renderedHeightPx / 2f,
    )
    val leftPoint = pointAtClockwiseDegrees(
        center,
        token.rotationDegrees + 270.0,
        renderedWidthPx / 2f,
    )

    val markerBaseDegrees = token.orientationMarkerBaseDegrees
    val markerEdgeRadius = orientationAxisRadius(
        renderedWidthPx,
        renderedHeightPx,
        markerBaseDegrees,
    )
    val markerRadius = (markerEdgeRadius - with(density) { 3.dp.toPx() }).coerceAtLeast(0f)
    val markerDegrees = token.rotationDegrees + markerBaseDegrees
    val markerEndpoint = pointAtClockwiseDegrees(center, markerDegrees, markerRadius)
    val rotationHandlePoint = pointAtClockwiseDegrees(
        center,
        markerDegrees,
        markerRadius + with(density) { 28.dp.toPx() },
    )

    if (!token.rotationLocked) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .zIndex(2f),
        ) {
            drawLine(
                color = Color(0xCC20343F),
                start = markerEndpoint,
                end = rotationHandlePoint,
                strokeWidth = 2.dp.toPx(),
            )
        }
    }

    if (!token.scaleLocked) {
        TokenControlHandle(
            screenPosition = topPoint,
            controlKey = "${token.id}-height-top",
            style = TokenControlHandleStyle.SCALE,
            onDragStart = { state.beginTokenResize(token.id) },
            onDragTo = {
                state.resizeTokenFromScreenPoint(token.id, TokenResizeAxis.HEIGHT, it)
            },
            onDragEnd = { state.finishTokenManipulation(token.id) },
            onDragCancel = { state.finishTokenManipulation(token.id) },
        )
        TokenControlHandle(
            screenPosition = rightPoint,
            controlKey = "${token.id}-width-right",
            style = TokenControlHandleStyle.SCALE,
            onDragStart = { state.beginTokenResize(token.id) },
            onDragTo = {
                state.resizeTokenFromScreenPoint(token.id, TokenResizeAxis.WIDTH, it)
            },
            onDragEnd = { state.finishTokenManipulation(token.id) },
            onDragCancel = { state.finishTokenManipulation(token.id) },
        )
        TokenControlHandle(
            screenPosition = bottomPoint,
            controlKey = "${token.id}-height-bottom",
            style = TokenControlHandleStyle.SCALE,
            onDragStart = { state.beginTokenResize(token.id) },
            onDragTo = {
                state.resizeTokenFromScreenPoint(token.id, TokenResizeAxis.HEIGHT, it)
            },
            onDragEnd = { state.finishTokenManipulation(token.id) },
            onDragCancel = { state.finishTokenManipulation(token.id) },
        )
        TokenControlHandle(
            screenPosition = leftPoint,
            controlKey = "${token.id}-width-left",
            style = TokenControlHandleStyle.SCALE,
            onDragStart = { state.beginTokenResize(token.id) },
            onDragTo = {
                state.resizeTokenFromScreenPoint(token.id, TokenResizeAxis.WIDTH, it)
            },
            onDragEnd = { state.finishTokenManipulation(token.id) },
            onDragCancel = { state.finishTokenManipulation(token.id) },
        )
    }

    if (!token.rotationLocked) {
        TokenControlHandle(
            screenPosition = rotationHandlePoint,
            controlKey = "${token.id}-rotation",
            style = TokenControlHandleStyle.ROTATE,
            onDragStart = { state.beginTokenRotation(token.id) },
            onDragTo = { state.rotateTokenFromScreenPoint(token.id, it) },
            onDragEnd = { state.finishTokenManipulation(token.id) },
            onDragCancel = { state.finishTokenManipulation(token.id) },
        )
    }

    state.activeTokenManipulation
        ?.takeIf { it.tokenId == token.id }
        ?.let { manipulation ->
            val rotatedHalfHeightPx = (
                renderedWidthPx * abs(sin(Math.toRadians(token.rotationDegrees))).toFloat() +
                    renderedHeightPx * abs(cos(Math.toRadians(token.rotationDegrees))).toFloat()
                ) / 2f
            TokenManipulationIndicator(
                center = center,
                tokenHalfHeightPx = rotatedHalfHeightPx,
                text = manipulationText(token, manipulation.kind),
            )
        }
}

@Composable
private fun BoxScope.TokenManipulationIndicator(
    center: Offset,
    tokenHalfHeightPx: Float,
    text: String,
) {
    val density = LocalDensity.current
    val indicatorWidth = 180.dp
    val indicatorWidthPx = with(density) { indicatorWidth.toPx() }
    val gapPx = with(density) { 12.dp.toPx() }

    Surface(
        modifier = Modifier
            .zIndex(4f)
            .offsetInPixels(
                x = center.x - indicatorWidthPx / 2f,
                y = center.y + tokenHalfHeightPx + gapPx,
            )
            .width(indicatorWidth),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun BoxScope.TokenControlHandle(
    screenPosition: Offset,
    controlKey: String,
    style: TokenControlHandleStyle,
    onDragStart: () -> Unit,
    onDragTo: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val density = LocalDensity.current
    val touchTargetDp = 24.dp
    val touchTargetPx = with(density) { touchTargetDp.toPx() }
    val currentScreenPosition = rememberUpdatedState(screenPosition)
    val currentOnDragStart = rememberUpdatedState(onDragStart)
    val currentOnDragTo = rememberUpdatedState(onDragTo)
    val currentOnDragEnd = rememberUpdatedState(onDragEnd)
    val currentOnDragCancel = rememberUpdatedState(onDragCancel)

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
                        currentOnDragStart.value()
                        val touchCenter = Offset(touchTargetPx / 2f, touchTargetPx / 2f)
                        pointerScreenPosition =
                            currentScreenPosition.value + (localStart - touchCenter)
                    },
                    onDragEnd = { currentOnDragEnd.value() },
                    onDragCancel = { currentOnDragCancel.value() },
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
                    drawRect(Color.White, topLeft = topLeft, size = Size(visualSize, visualSize))
                    drawRect(
                        color = Color(0xFFFF9800),
                        topLeft = topLeft,
                        size = Size(visualSize, visualSize),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }

                TokenControlHandleStyle.ROTATE -> {
                    drawCircle(Color(0xFF6A4C93), radius = visualSize / 2f, center = visualCenter)
                    drawCircle(
                        color = Color.White,
                        radius = visualSize / 2f,
                        center = visualCenter,
                        style = Stroke(width = 2.dp.toPx()),
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

private fun manipulationText(
    token: TabletopToken,
    kind: TokenManipulationKind,
): String =
    when (kind) {
        TokenManipulationKind.SCALE ->
            "Size ${formatManipulationNumber(token.widthCells)} × " +
                "${formatManipulationNumber(token.heightCells)} cells"
        TokenManipulationKind.ROTATION ->
            "Rotation ${token.rotationDegrees.roundToInt()}°"
    }

private fun formatManipulationNumber(value: Double): String {
    val roundedInteger = value.roundToInt()
    if (abs(value - roundedInteger.toDouble()) < 0.000_001) return roundedInteger.toString()
    val roundedTenth = (value * 10.0).roundToInt() / 10.0
    return roundedTenth.toString().removeSuffix(".0")
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

    rotate(degrees = token.rotationDegrees.toFloat(), pivot = tokenCenter) {
        drawOval(
            color = Color(token.colorArgb),
            topLeft = ovalTopLeft,
            size = ovalSize,
        )

        val markerRadius = (
            orientationAxisRadius(
                ovalSize.width,
                ovalSize.height,
                token.orientationMarkerBaseDegrees,
            ) - 3f * density
            ).coerceAtLeast(0f)
        val markerEnd = pointAtClockwiseDegrees(
            tokenCenter,
            token.orientationMarkerBaseDegrees,
            markerRadius,
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
    val visibleBounds = state.transform.visibleWorldRect()
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
                    if (index == 0) path.moveTo(screen.x, screen.y)
                    else path.lineTo(screen.x, screen.y)
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
        if (index == 0) path.moveTo(screen.x, screen.y)
        else path.lineTo(screen.x, screen.y)
    }
    drawPath(
        path = path,
        color = Color(stroke.colorArgb),
        style = Stroke(
            width = (stroke.widthWorldUnits * state.pixelsPerWorldUnit)
                .toFloat()
                .coerceAtLeast(1f),
        ),
    )
}

private fun DrawScope.drawMeasurement(path: MeasurementPath, state: TabletopState) {
    if (path.points.isEmpty()) return
    val lineColor = Color(0xFFD35400)
    val selectedColor = Color(0xFFFFB300)

    path.points.zipWithNext().forEach { (startWorld, endWorld) ->
        drawLine(
            color = lineColor,
            start = state.worldToScreen(startWorld),
            end = state.worldToScreen(endWorld),
            strokeWidth = 4f * density,
        )
    }

    path.points.forEachIndexed { index, point ->
        val selected = index == state.selectedMeasurementMarkerIndex
        drawCircle(
            color = if (selected) selectedColor else lineColor,
            radius = if (selected) 8f * density else 6f * density,
            center = state.worldToScreen(point),
        )
        if (selected) {
            drawCircle(
                color = Color.White,
                radius = 4f * density,
                center = state.worldToScreen(point),
            )
        }
    }
}

fun measurementText(state: TabletopState): String? {
    val path = state.measurement ?: return null
    if (path.points.isEmpty()) return null
    if (path.points.size == 1) return "1 measurement marker"

    val segments = path.points.zipWithNext().map { (start, end) ->
        when (state.gridKind) {
            GridKind.SQUARE -> MeasurementEngine.measureSquare(
                start,
                end,
                state.squareGrid,
                state.unitScale,
            )
            GridKind.HEX -> MeasurementEngine.measureHex(
                start,
                end,
                state.hexGrid,
                state.unitScale,
            )
        }.formatted()
    }
    return "${segments.size} segment${if (segments.size == 1) "" else "s"}: " +
        segments.joinToString(" + ")
}
