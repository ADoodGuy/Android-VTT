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
import kotlin.math.hypot

fun Modifier.tabletopGestures(state: TabletopState): Modifier = pointerInput(state.tool) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
        val gestureTool = state.tool
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
                when (gestureTool) {
                    TabletopTool.MEASURE -> state.beginMeasurement(down.position)
                    TabletopTool.DRAW -> state.beginStroke(down.position)
                    TabletopTool.PAN -> Unit
                }
                toolActionStarted = true
            }

            when (gestureTool) {
                TabletopTool.PAN -> state.panBy(delta)
                TabletopTool.MEASURE -> state.updateMeasurement(change.position)
                TabletopTool.DRAW -> state.appendStrokePoint(change.position)
            }
            change.consume()
        }

        if (!transformed) {
            when (gestureTool) {
                TabletopTool.PAN -> {
                    if (totalMovement < viewConfiguration.touchSlop) {
                        state.clearTokenSelection()
                    }
                }

                TabletopTool.MEASURE -> Unit
                TabletopTool.DRAW -> if (toolActionStarted) state.finishStroke()
            }
        } else if (gestureTool == TabletopTool.DRAW && toolActionStarted) {
            state.finishStroke()
        }
    }
}
