package com.adoodguy.androidvtt.tabletop

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.adoodguy.androidvtt.geometry.GridKind
import com.adoodguy.androidvtt.geometry.MeasurementEngine
import com.adoodguy.androidvtt.geometry.MeasurementResult
import com.adoodguy.androidvtt.geometry.WorldPoint
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.roundToInt

@Composable
fun BoxScope.MeasurementOverlay(state: TabletopState) {
    val path = state.measurement ?: return

    path.points.zipWithNext().forEach { (start, end) ->
        val startScreen = state.worldToScreen(start)
        val endScreen = state.worldToScreen(end)
        val midpoint = Offset(
            x = (startScreen.x + endScreen.x) / 2f,
            y = (startScreen.y + endScreen.y) / 2f,
        )
        val dx = endScreen.x - startScreen.x
        val dy = endScreen.y - startScreen.y
        val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val density = LocalDensity.current
        val labelGap = with(density) { 12.dp.toPx() }
        val labelPoint = if (length > 0.001f) {
            Offset(
                x = midpoint.x - dy / length * labelGap,
                y = midpoint.y + dx / length * labelGap,
            )
        } else {
            midpoint
        }

        Surface(
            modifier = Modifier
                .zIndex(8f)
                .measurementOffsetInPixels(labelPoint.x + 4f, labelPoint.y + 4f),
            shape = RoundedCornerShape(5.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            shadowElevation = 1.dp,
        ) {
            Text(
                text = measureSegment(state, start, end).formatted(),
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }

    if (
        WorkspaceModeStore.mode != TabletopMode.TOOLS ||
        state.tool != TabletopTool.MEASURE
    ) {
        return
    }

    val density = LocalDensity.current
    val touchTarget = 48.dp
    val touchTargetPx = with(density) { touchTarget.toPx() }
    val hitRadiusPx = touchTargetPx / 2f

    path.points.forEachIndexed { index, point ->
        val screenPosition = state.worldToScreen(point)
        val currentScreenPosition = rememberUpdatedState(screenPosition)

        Box(
            modifier = Modifier
                .zIndex(32f)
                .measurementOffsetInPixels(
                    x = screenPosition.x - touchTargetPx / 2f,
                    y = screenPosition.y - touchTargetPx / 2f,
                )
                .size(touchTarget)
                .pointerInput(index, path) {
                    detectTapGestures(
                        onTap = {
                            state.handleMeasurementTap(
                                currentScreenPosition.value,
                                hitRadiusPx,
                            )
                        },
                    )
                }
                .pointerInput(
                    index,
                    path,
                    state.pixelsPerWorldUnit,
                    state.gridKind,
                    state.hexOrientation,
                    state.snapEnabled,
                ) {
                    var pointerScreenPosition = currentScreenPosition.value
                    detectDragGestures(
                        onDragStart = { localStart ->
                            state.dismissMeasurementMarkerMenu()
                            val touchCenter = Offset(touchTargetPx / 2f, touchTargetPx / 2f)
                            pointerScreenPosition =
                                currentScreenPosition.value + (localStart - touchCenter)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            pointerScreenPosition += dragAmount
                            val currentPath = state.measurement
                            if (currentPath != null && index in currentPath.points.indices) {
                                currentPath.points[index] =
                                    state.snappedWorldPoint(pointerScreenPosition)
                            }
                        },
                    )
                },
        )
    }
}

fun measurementTotalText(state: TabletopState): String? {
    val path = state.measurement ?: return null
    if (path.points.isEmpty()) return null

    val results = path.points.zipWithNext().map { (start, end) ->
        measureSegment(state, start, end)
    }
    val displayedDistance = results.sumOf { it.displayedDistance }
    val gridSteps = results.sumOf { it.gridSteps }
    val unit = state.unitScale.unitAbbreviation
    return "Total ${formatDistance(displayedDistance)} $unit • $gridSteps cells"
}

private fun measureSegment(
    state: TabletopState,
    start: WorldPoint,
    end: WorldPoint,
): MeasurementResult =
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
    }

private fun formatDistance(value: Double): String =
    if (value == round(value)) value.roundToInt().toString() else "%.1f".format(value)

private fun Modifier.measurementOffsetInPixels(x: Float, y: Float): Modifier =
    this.then(
        Modifier.offset {
            IntOffset(x.roundToInt(), y.roundToInt())
        },
    )
