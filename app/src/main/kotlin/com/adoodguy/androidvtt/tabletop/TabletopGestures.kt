package com.adoodguy.androidvtt.tabletop

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.adoodguy.androidvtt.geometry.WorldPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

fun Modifier.tabletopGestures(state: TabletopState): Modifier =
    pointerInput(
        WorkspaceModeStore.mode,
        state.tool,
        state.drawingMode,
        TabletopMapStore.alignmentVisible,
    ) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
            val gestureMode = WorkspaceModeStore.mode
            val gestureTool = state.tool
            val alignmentGesture =
                gestureMode == TabletopMode.MAPS && TabletopMapStore.alignmentVisible
            val eraserRadiusPx = 18.dp.toPx()
            val markerHitRadiusPx = 24.dp.toPx()
            var transformed = false
            var totalMovement = 0.0
            var lastSinglePosition = down.position
            var toolActionStarted = false

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Main)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break

                if (pressed.size >= 2) {
                    transformed = true
                    val pan = event.calculatePan()
                    val zoom = event.calculateZoom()
                    val centroid = event.calculateCentroid(useCurrent = true)
                    state.transformBy(pan, zoom, centroid)
                    event.changes.forEach { it.consume() }
                    continue
                }

                if (transformed) continue

                val change = pressed.single()
                val delta = change.position - lastSinglePosition
                lastSinglePosition = change.position
                totalMovement += hypot(delta.x.toDouble(), delta.y.toDouble())

                if (!toolActionStarted) {
                    when (gestureMode) {
                        TabletopMode.TOKENS -> Unit

                        TabletopMode.MAPS -> {
                            if (alignmentGesture) {
                                TabletopMapStore.beginMove()
                            }
                        }

                        TabletopMode.TOOLS -> when (gestureTool) {
                            TabletopTool.DRAW -> state.beginDrawing(down.position, eraserRadiusPx)
                            TabletopTool.PAN,
                            TabletopTool.MEASURE,
                            TabletopTool.NOTES,
                            -> Unit
                        }
                    }
                    toolActionStarted = true
                }

                when (gestureMode) {
                    TabletopMode.TOKENS -> state.panBy(delta)

                    TabletopMode.MAPS -> {
                        if (alignmentGesture) {
                            TabletopMapStore.moveByScreenDelta(state, delta)
                        } else {
                            state.panBy(delta)
                        }
                    }

                    TabletopMode.TOOLS -> when (gestureTool) {
                        TabletopTool.PAN -> state.panBy(delta)
                        TabletopTool.DRAW -> state.continueDrawing(change.position, eraserRadiusPx)
                        TabletopTool.MEASURE,
                        TabletopTool.NOTES,
                        -> Unit
                    }
                }
                change.consume()
            }

            if (!transformed) {
                when (gestureMode) {
                    TabletopMode.TOKENS -> {
                        if (totalMovement < viewConfiguration.touchSlop) {
                            state.clearTokenSelection()
                        }
                    }

                    TabletopMode.MAPS -> {
                        if (alignmentGesture) {
                            if (toolActionStarted && totalMovement >= viewConfiguration.touchSlop) {
                                TabletopMapStore.finishMove(state)
                            }
                        } else if (totalMovement < viewConfiguration.touchSlop) {
                            if (screenPointIsInsideMap(state, down.position)) {
                                TabletopMapStore.selectAtScreenPoint(state, down.position)
                            } else {
                                TabletopMapStore.clearSelection()
                            }
                        }
                    }

                    TabletopMode.TOOLS -> when (gestureTool) {
                        TabletopTool.PAN -> Unit
                        TabletopTool.MEASURE -> {
                            if (totalMovement < viewConfiguration.touchSlop) {
                                state.handleMeasurementTap(down.position, markerHitRadiusPx)
                            }
                        }
                        TabletopTool.DRAW -> if (toolActionStarted) state.finishDrawing()
                        TabletopTool.NOTES -> {
                            if (totalMovement < viewConfiguration.touchSlop) {
                                state.addNoteAtScreenPoint(down.position)
                            }
                        }
                    }
                }
            } else if (
                gestureMode == TabletopMode.TOOLS &&
                gestureTool == TabletopTool.DRAW &&
                toolActionStarted
            ) {
                state.finishDrawing()
            }
        }
    }

private fun screenPointIsInsideMap(state: TabletopState, screenPoint: Offset): Boolean {
    val configuration = TabletopMapStore.configuration
    if (!configuration.hasImage) return false

    val center = state.worldToScreen(
        WorldPoint(configuration.centerX, configuration.centerY),
    )
    val deltaX = (screenPoint.x - center.x).toDouble()
    val deltaY = (screenPoint.y - center.y).toDouble()
    val radians = Math.toRadians(configuration.rotationDegrees)

    val localX = deltaX * cos(radians) + deltaY * sin(radians)
    val localY = -deltaX * sin(radians) + deltaY * cos(radians)
    val halfWidthPx =
        configuration.widthCells * state.cellSizeWorldUnits * state.pixelsPerWorldUnit / 2.0
    val halfHeightPx =
        configuration.heightCells * state.cellSizeWorldUnits * state.pixelsPerWorldUnit / 2.0

    return abs(localX) <= halfWidthPx && abs(localY) <= halfHeightPx
}
