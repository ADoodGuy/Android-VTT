package com.adoodguy.androidvtt.tabletop

import com.adoodguy.androidvtt.geometry.WorldPoint
import kotlin.math.abs

enum class TabletopTool {
    PAN,
    MEASURE,
    DRAW,
}

enum class TokenColorPreset(
    val label: String,
    val argb: Long,
) {
    RED("Red", 0xFFB5534BL),
    ORANGE("Orange", 0xFFD9772EL),
    YELLOW("Yellow", 0xFFE0B83EL),
    GREEN("Green", 0xFF4F7A5AL),
    CYAN("Cyan", 0xFF2E8B92L),
    BLUE("Blue", 0xFF4E6E81L),
    PURPLE("Purple", 0xFF735A8DL),
}

enum class TokenSizePreset(
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

enum class TokenOrientationMarkerAxis(val label: String) {
    MAJOR("Major axis"),
    MINOR("Minor axis"),
}

enum class TokenResizeAxis {
    WIDTH,
    HEIGHT,
}

enum class TokenManipulationKind {
    SCALE,
    ROTATION,
}

data class ActiveTokenManipulation(
    val tokenId: Long,
    val kind: TokenManipulationKind,
)

data class TabletopToken(
    val id: Long,
    val name: String,
    val position: WorldPoint,
    val widthCells: Double,
    val heightCells: Double,
    val colorArgb: Long,
    val rotationDegrees: Double,
    val orientationMarkerAxis: TokenOrientationMarkerAxis,
) {
    val isCircular: Boolean
        get() = abs(widthCells - heightCells) < 0.000_001

    /**
     * Clockwise angle from screen-up to the marker's unrotated local axis.
     * Token rotation is added to this angle when the marker is rendered.
     */
    val orientationMarkerBaseDegrees: Double
        get() {
            if (isCircular) return 0.0
            val widthIsMajor = widthCells >= heightCells
            return when (orientationMarkerAxis) {
                TokenOrientationMarkerAxis.MAJOR -> if (widthIsMajor) 90.0 else 0.0
                TokenOrientationMarkerAxis.MINOR -> if (widthIsMajor) 0.0 else 90.0
            }
        }
}

data class MeasurementLine(
    val start: WorldPoint,
    val end: WorldPoint,
)

data class DrawingStroke(
    val points: List<WorldPoint>,
    val widthWorldUnits: Double,
)
