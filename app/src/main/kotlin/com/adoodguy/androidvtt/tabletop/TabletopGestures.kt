package com.adoodguy.androidvtt.tabletop

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot

fun Modifier.tabletopGestures(state: TabletopState): Modifier =
    pointerInput(WorkspaceModeStore.mode, state.tool, TabletopMapStore.alignmentVisible) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
            val gestureMode = WorkspaceModeStore.mode
            val gestureTool = state.tool
            val alignmentGesture =
                gestureMode == TabletopMode.MAPS && TabletopMapStore.alignmentVisible
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
                            TabletopTool.MEASURE -> state.beginMeasurement(down.position)
                            TabletopTool.DRAW -> state.beginStroke(down.position)
                            TabletopTool.PAN -> Unit
                        }
                    }
                    toolActionStarted = true
                }

                when (gestureMode) {
                    TabletopMode.TOKENS -> state.panBy(delta)

                    TabletopMode.MAPS -> {
                        if (alignmentGesture) {
                            // Alignment anchor movement intentionally uses the entire
                            // tabletop viewport rather than the map's visual bounds.
                            // This keeps precise crosshair placement available even
                            // when a highly zoomed map is larger than the viewport.
                            TabletopMapStore.moveByScreenDelta(state, delta)
                        } else {
                            state.panBy(delta)
                        }
                    }

                    TabletopMode.TOOLS -> when (gestureTool) {
                        TabletopTool.PAN -> state.panBy(delta)
                        TabletopTool.MEASURE -> state.updateMeasurement(change.position)
                        TabletopTool.DRAW -> state.appendStrokePoint(change.position)
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
                            TabletopMapStore.clearSelection()
                        }
                    }

                    TabletopMode.TOOLS -> when (gestureTool) {
                        TabletopTool.PAN -> Unit
                        TabletopTool.MEASURE -> Unit
                        TabletopTool.DRAW -> if (toolActionStarted) state.finishStroke()
                    }
                }
            } else if (
                gestureMode == TabletopMode.TOOLS &&
                gestureTool == TabletopTool.DRAW &&
                toolActionStarted
            ) {
                state.finishStroke()
            }
        }
    }
