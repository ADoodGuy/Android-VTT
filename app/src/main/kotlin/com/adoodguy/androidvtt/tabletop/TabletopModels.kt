package com.adoodguy.androidvtt.tabletop

import com.adoodguy.androidvtt.geometry.WorldPoint

enum class TabletopTool {
    PAN,
    MEASURE,
    DRAW,
}

data class MeasurementLine(
    val start: WorldPoint,
    val end: WorldPoint,
)

data class DrawingStroke(
    val points: List<WorldPoint>,
    val widthWorldUnits: Double,
)
