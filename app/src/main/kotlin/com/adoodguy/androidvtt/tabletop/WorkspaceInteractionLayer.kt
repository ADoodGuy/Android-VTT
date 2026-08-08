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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.adoodguy.androidvtt.geometry.WorldPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun BoxScope.WorkspaceInteractionLayer(state: TabletopState) {
    when (WorkspaceModeStore.mode) {
        TabletopMode.TOKENS -> Unit

        TabletopMode.TOOLS -> {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(8f)
                    .tabletopGestures(state),
            )
        }

        TabletopMode.MAPS -> {
            MapInteractionLayer(state)
        }
    }
}

@Composable
private fun BoxScope.MapInteractionLayer(state: TabletopState) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .zIndex(8f)
            .tabletopGestures(state),
    )

    val configuration = TabletopMapStore.configuration
    if (!configuration.hasImage) return

    val density = LocalDensity.current
    val center = state.worldToScreen(WorldPoint(configuration.centerX, configuration.centerY))
    val widthPx = (
        configuration.widthCells * state.cellSizeWorldUnits * state.pixelsPerWorldUnit
        ).toFloat()
    val heightPx = (
        configuration.heightCells * state.cellSizeWorldUnits * state.pixelsPerWorldUnit
        ).toFloat()

    val radians = Math.toRadians(configuration.rotationDegrees)
    val absoluteCosine = abs(cos(radians)).toFloat()
    val absoluteSine = abs(sin(radians)).toFloat()
    val rotatedWidthPx = widthPx * absoluteCosine + heightPx * absoluteSine
    val rotatedHeightPx = widthPx * absoluteSine + heightPx * absoluteCosine
    val minimumTouchPx = with(density) { 48.dp.toPx() }
    val containerWidthPx = maxOf(rotatedWidthPx, minimumTouchPx)
    val containerHeightPx = maxOf(rotatedHeightPx, minimumTouchPx)
    val alignmentVisible = TabletopMapStore.alignmentVisible

    // During ordinary map editing the map body owns its own tap/drag target.
    // During alignment that target is intentionally removed: the full tabletop
    // interaction layer handles one-finger crosshair movement instead. This
    // avoids large, zoomed maps being constrained by a viewport-sized hit box.
    if (!alignmentVisible) {
        Box(
            modifier = Modifier
                .zIndex(9f)
                .mapOffsetInPixels(
                    x = center.x - containerWidthPx / 2f,
                    y = center.y - containerHeightPx / 2f,
                )
                .size(
                    with(density) { containerWidthPx.toDp() },
                    with(density) { containerHeightPx.toDp() },
                )
                .pointerInput(configuration.imageUri) {
                    detectTapGestures(
                        onTap = { TabletopMapStore.toggleSelection() },
                        onDoubleTap = { TabletopMapStore.clearSelection() },
                        onLongPress = { TabletopMapStore.openSettings() },
                    )
                }
                .pointerInput(
                    configuration.imageUri,
                    state.pixelsPerWorldUnit,
                    state.snapEnabled,
                ) {
                    detectDragGestures(
                        onDragStart = { TabletopMapStore.beginMove() },
                        onDragEnd = { TabletopMapStore.finishMove(state) },
                        onDragCancel = { TabletopMapStore.finishMove(state) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            TabletopMapStore.moveByScreenDelta(state, dragAmount)
                        },
                    )
                },
        )
    }

    if (!TabletopMapStore.selected) return

    // Keep the selected-map outline visible in both ordinary and alignment modes.
    Canvas(
        modifier = Modifier
            .matchParentSize()
            .zIndex(10f),
    ) {
        rotate(configuration.rotationDegrees.toFloat(), pivot = center) {
            drawRect(
                color = Color(0xFFFFB300),
                topLeft = Offset(center.x - widthPx / 2f, center.y - heightPx / 2f),
                size = Size(widthPx, heightPx),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }

    if (alignmentVisible) {
        MapAlignmentController(
            state = state,
            configuration = configuration,
        )
        return
    }

    val topPoint = pointAtMapDegrees(
        center = center,
        degrees = configuration.rotationDegrees,
        distance = heightPx / 2f,
    )
    val rightPoint = pointAtMapDegrees(
        center = center,
        degrees = configuration.rotationDegrees + 90.0,
        distance = widthPx / 2f,
    )
    val bottomPoint = pointAtMapDegrees(
        center = center,
        degrees = configuration.rotationDegrees + 180.0,
        distance = heightPx / 2f,
    )
    val leftPoint = pointAtMapDegrees(
        center = center,
        degrees = configuration.rotationDegrees + 270.0,
        distance = widthPx / 2f,
    )
    val rotationGapPx = with(density) { 28.dp.toPx() }
    val rotationHandlePoint = pointAtMapDegrees(
        center = center,
        degrees = configuration.rotationDegrees,
        distance = heightPx / 2f + rotationGapPx,
    )

    Canvas(
        modifier = Modifier
            .matchParentSize()
            .zIndex(10f),
    ) {
        drawLine(
            color = Color(0xCC20343F),
            start = topPoint,
            end = rotationHandlePoint,
            strokeWidth = 2.dp.toPx(),
        )
    }

    MapControlHandle(
        screenPosition = topPoint,
        controlKey = "map-height-top",
        style = MapHandleStyle.SCALE,
        onDragStart = TabletopMapStore::beginResize,
        onDragTo = { TabletopMapStore.resizeFromScreenPoint(state, MapResizeAxis.HEIGHT, it) },
        onDragEnd = TabletopMapStore::finishManipulation,
        onDragCancel = TabletopMapStore::finishManipulation,
    )
    MapControlHandle(
        screenPosition = rightPoint,
        controlKey = "map-width-right",
        style = MapHandleStyle.SCALE,
        onDragStart = TabletopMapStore::beginResize,
        onDragTo = { TabletopMapStore.resizeFromScreenPoint(state, MapResizeAxis.WIDTH, it) },
        onDragEnd = TabletopMapStore::finishManipulation,
        onDragCancel = TabletopMapStore::finishManipulation,
    )
    MapControlHandle(
        screenPosition = bottomPoint,
        controlKey = "map-height-bottom",
        style = MapHandleStyle.SCALE,
        onDragStart = TabletopMapStore::beginResize,
        onDragTo = { TabletopMapStore.resizeFromScreenPoint(state, MapResizeAxis.HEIGHT, it) },
        onDragEnd = TabletopMapStore::finishManipulation,
        onDragCancel = TabletopMapStore::finishManipulation,
    )
    MapControlHandle(
        screenPosition = leftPoint,
        controlKey = "map-width-left",
        style = MapHandleStyle.SCALE,
        onDragStart = TabletopMapStore::beginResize,
        onDragTo = { TabletopMapStore.resizeFromScreenPoint(state, MapResizeAxis.WIDTH, it) },
        onDragEnd = TabletopMapStore::finishManipulation,
        onDragCancel = TabletopMapStore::finishManipulation,
    )
    MapControlHandle(
        screenPosition = rotationHandlePoint,
        controlKey = "map-rotation",
        style = MapHandleStyle.ROTATE,
        onDragStart = TabletopMapStore::beginRotation,
        onDragTo = { TabletopMapStore.rotateFromScreenPoint(state, it) },
        onDragEnd = TabletopMapStore::finishManipulation,
        onDragCancel = TabletopMapStore::finishManipulation,
    )

    TabletopMapStore.activeManipulation?.let { manipulation ->
        MapManipulationIndicator(
            center = center,
            tokenHalfHeightPx = rotatedHeightPx / 2f,
            text = mapManipulationText(configuration, manipulation),
        )
    }
}

private data class AlignmentScaleGestureBase(
    val widthCells: Double,
    val snapAnchorU: Double,
    val rotationDegrees: Double,
)

@Composable
private fun BoxScope.MapAlignmentController(
    state: TabletopState,
    configuration: TabletopMapConfiguration,
) {
    val density = LocalDensity.current
    val anchor = state.worldToScreen(TabletopMapStore.snapAnchorWorld(state))
    val scaleRadiusPx = with(density) { 96.dp.toPx() }
    val rotationRadiusPx = with(density) { 60.dp.toPx() }
    val controllerMarginPx = with(density) { 28.dp.toPx() }

    // Prefer the side with more visible room so the fixed-size scale controller
    // stays reachable when the alignment anchor is close to a screen edge.
    val scaleDirection = if (
        anchor.x + scaleRadiusPx + controllerMarginPx <= state.viewportSize.width.toFloat()
    ) {
        1f
    } else {
        -1f
    }
    val scaleHandlePoint = Offset(
        x = anchor.x + scaleDirection * scaleRadiusPx,
        y = anchor.y,
    )
    val rotationHandlePoint = pointAtMapDegrees(
        center = anchor,
        degrees = configuration.rotationDegrees,
        distance = rotationRadiusPx,
    )

    Canvas(
        modifier = Modifier
            .matchParentSize()
            .zIndex(10f),
    ) {
        drawCircle(
            color = Color(0x996A4C93),
            radius = rotationRadiusPx,
            center = anchor,
            style = Stroke(width = 2.dp.toPx()),
        )
        drawLine(
            color = Color(0xFFFF9800),
            start = anchor,
            end = scaleHandlePoint,
            strokeWidth = 3.dp.toPx(),
        )
    }

    var scaleBase by remember(configuration.imageUri) {
        mutableStateOf<AlignmentScaleGestureBase?>(null)
    }

    MapControlHandle(
        screenPosition = scaleHandlePoint,
        controlKey = "map-alignment-scale",
        style = MapHandleStyle.SCALE,
        touchTargetDp = 36.dp,
        onDragStart = {
            scaleBase = AlignmentScaleGestureBase(
                widthCells = configuration.widthCells,
                snapAnchorU = configuration.snapAnchorU,
                rotationDegrees = configuration.rotationDegrees,
            )
            TabletopMapStore.beginResize()
        },
        onDragTo = { pointer ->
            val base = scaleBase ?: return@MapControlHandle
            if (base.widthCells <= 0.0 || !base.widthCells.isFinite()) {
                return@MapControlHandle
            }

            val currentAnchor = state.worldToScreen(TabletopMapStore.snapAnchorWorld(state))
            val controllerDistance = hypot(
                (pointer.x - currentAnchor.x).toDouble(),
                (pointer.y - currentAnchor.y).toDouble(),
            )
            val scaleFactor = (controllerDistance / scaleRadiusPx.toDouble())
                .coerceAtLeast(0.001)

            // Feed the existing anchor-preserving resize engine a synthetic point
            // on the map's local width axis. This keeps all existing scale bounds
            // and magnetic snapping behavior while giving alignment a compact,
            // zoom-independent controller.
            val rightDistanceCells = abs((0.5 - base.snapAnchorU) * base.widthCells)
            val leftDistanceCells = abs((-0.5 - base.snapAnchorU) * base.widthCells)
            val localSide = if (rightDistanceCells >= leftDistanceCells) 1.0 else -1.0
            val baseDistanceCells = maxOf(rightDistanceCells, leftDistanceCells)
            if (baseDistanceCells < 0.000_001) return@MapControlHandle

            val pixelsPerCell = state.pixelsPerWorldUnit * state.cellSizeWorldUnits
            val syntheticLocalX =
                localSide * baseDistanceCells * scaleFactor * pixelsPerCell
            val mapRadians = Math.toRadians(base.rotationDegrees)
            val syntheticPoint = Offset(
                x = currentAnchor.x + (syntheticLocalX * cos(mapRadians)).toFloat(),
                y = currentAnchor.y + (syntheticLocalX * sin(mapRadians)).toFloat(),
            )
            TabletopMapStore.resizeFromScreenPoint(
                state = state,
                axis = MapResizeAxis.WIDTH,
                screenPoint = syntheticPoint,
            )
        },
        onDragEnd = {
            scaleBase = null
            TabletopMapStore.finishManipulation()
        },
        onDragCancel = {
            scaleBase = null
            TabletopMapStore.finishManipulation()
        },
    )

    MapControlHandle(
        screenPosition = rotationHandlePoint,
        controlKey = "map-alignment-rotation",
        style = MapHandleStyle.ROTATE,
        touchTargetDp = 36.dp,
        onDragStart = TabletopMapStore::beginRotation,
        onDragTo = { pointer ->
            val currentAnchor = state.worldToScreen(TabletopMapStore.snapAnchorWorld(state))
            val deltaX = (pointer.x - currentAnchor.x).toDouble()
            val deltaY = (pointer.y - currentAnchor.y).toDouble()
            if (abs(deltaX) < 0.000_001 && abs(deltaY) < 0.000_001) {
                return@MapControlHandle
            }

            val desiredDegrees = Math.toDegrees(atan2(deltaX, -deltaY))
            val currentConfiguration = TabletopMapStore.configuration
            val currentCenter = state.worldToScreen(
                WorldPoint(currentConfiguration.centerX, currentConfiguration.centerY),
            )
            val syntheticPoint = pointAtMapDegrees(
                center = currentCenter,
                degrees = desiredDegrees,
                distance = 100f,
            )
            TabletopMapStore.rotateFromScreenPoint(state, syntheticPoint)
        },
        onDragEnd = TabletopMapStore::finishManipulation,
        onDragCancel = TabletopMapStore::finishManipulation,
    )

    TabletopMapStore.activeManipulation?.let { manipulation ->
        val currentConfiguration = TabletopMapStore.configuration
        MapManipulationIndicator(
            center = anchor,
            tokenHalfHeightPx = with(density) { 76.dp.toPx() },
            text = mapManipulationText(currentConfiguration, manipulation),
        )
    }
}

@Composable
private fun BoxScope.MapControlHandle(
    screenPosition: Offset,
    controlKey: String,
    style: MapHandleStyle,
    touchTargetDp: Dp = 28.dp,
    onDragStart: () -> Unit,
    onDragTo: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val density = LocalDensity.current
    val touchTargetPx = with(density) { touchTargetDp.toPx() }
    val currentPosition = rememberUpdatedState(screenPosition)
    val currentOnDragStart = rememberUpdatedState(onDragStart)
    val currentOnDragTo = rememberUpdatedState(onDragTo)
    val currentOnDragEnd = rememberUpdatedState(onDragEnd)
    val currentOnDragCancel = rememberUpdatedState(onDragCancel)

    Box(
        modifier = Modifier
            .zIndex(11f)
            .mapOffsetInPixels(
                x = screenPosition.x - touchTargetPx / 2f,
                y = screenPosition.y - touchTargetPx / 2f,
            )
            .size(touchTargetDp)
            .pointerInput(controlKey) {
                var pointerScreenPosition = Offset.Zero
                detectDragGestures(
                    onDragStart = { localStart ->
                        currentOnDragStart.value()
                        val center = Offset(touchTargetPx / 2f, touchTargetPx / 2f)
                        pointerScreenPosition = currentPosition.value + (localStart - center)
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
            val visualSize = 10.dp.toPx()
            when (style) {
                MapHandleStyle.SCALE -> {
                    val topLeft = Offset(
                        center.x - visualSize / 2f,
                        center.y - visualSize / 2f,
                    )
                    drawRect(Color.White, topLeft = topLeft, size = Size(visualSize, visualSize))
                    drawRect(
                        color = Color(0xFFFF9800),
                        topLeft = topLeft,
                        size = Size(visualSize, visualSize),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }

                MapHandleStyle.ROTATE -> {
                    drawCircle(Color(0xFF6A4C93), radius = visualSize / 2f)
                    drawCircle(
                        color = Color.White,
                        radius = visualSize / 2f,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.MapManipulationIndicator(
    center: Offset,
    tokenHalfHeightPx: Float,
    text: String,
) {
    val density = LocalDensity.current
    val indicatorWidth = 190.dp
    val indicatorWidthPx = with(density) { indicatorWidth.toPx() }
    val gapPx = with(density) { 12.dp.toPx() }

    Surface(
        modifier = Modifier
            .zIndex(12f)
            .mapOffsetInPixels(
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

private enum class MapHandleStyle {
    SCALE,
    ROTATE,
}

private fun mapManipulationText(
    configuration: TabletopMapConfiguration,
    manipulation: MapManipulationKind,
): String =
    when (manipulation) {
        MapManipulationKind.SCALE ->
            "Size ${formatMapManipulation(configuration.widthCells)} × " +
                "${formatMapManipulation(configuration.heightCells)} cells"

        MapManipulationKind.ROTATION ->
            "Rotation ${configuration.rotationDegrees.roundToInt()}°"
    }

private fun pointAtMapDegrees(center: Offset, degrees: Double, distance: Float): Offset {
    val radians = Math.toRadians(degrees)
    return Offset(
        x = center.x + sin(radians).toFloat() * distance,
        y = center.y - cos(radians).toFloat() * distance,
    )
}

private fun Modifier.mapOffsetInPixels(x: Float, y: Float): Modifier =
    this.then(
        Modifier.offset {
            IntOffset(x.roundToInt(), y.roundToInt())
        },
    )

private fun formatMapManipulation(value: Double): String {
    val roundedInteger = value.roundToInt()
    if (abs(value - roundedInteger.toDouble()) < 0.000_001) return roundedInteger.toString()
    return ((value * 10.0).roundToInt() / 10.0).toString().removeSuffix(".0")
}
