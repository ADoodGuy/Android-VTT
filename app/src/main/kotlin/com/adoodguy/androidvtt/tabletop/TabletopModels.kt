package com.adoodguy.androidvtt.tabletop

import com.adoodguy.androidvtt.geometry.WorldPoint

enum class TabletopTool {
    PAN,
    MEASURE,
    DRAW,
}

enum class TokenColor(val label: String) {
    RED("Red"),
    ORANGE("Orange"),
    YELLOW("Yellow"),
    GREEN("Green"),
    CYAN("Cyan"),
    BLUE("Blue"),
    PURPLE("Purple"),
}

enum class TokenFootprint(
    val widthCells: Double,
    val heightCells: Double,
    val label: String,
) {
    HALF_BY_HALF(0.5, 0.5, "0.5 × 0.5 cell"),
    ONE_BY_ONE(1.0, 1.0, "1 × 1 cell"),
    ONE_BY_TWO(1.0, 2.0, "1 × 2 cells"),
    TWO_BY_TWO(2.0, 2.0, "2 × 2 cells"),
    TWO_BY_FOUR(2.0, 4.0, "2 × 4 cells"),
}

data class TabletopToken(
    val id: Long,
    val name: String,
    val position: WorldPoint,
    val footprint: TokenFootprint,
    val color: TokenColor,
)

data class MeasurementLine(
    val start: WorldPoint,
    val end: WorldPoint,
)

data class DrawingStroke(
    val points: List<WorldPoint>,
    val widthWorldUnits: Double,
)
