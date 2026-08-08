package com.adoodguy.androidvtt.tabletop

import androidx.compose.runtime.mutableStateListOf
import com.adoodguy.androidvtt.geometry.WorldPoint
import kotlin.math.abs

enum class TabletopMode {
    TOKENS,
    MAPS,
    TOOLS,
}

enum class TabletopTool {
    PAN,
    MEASURE,
    DRAW,
    NOTES,
    DICE,
}

enum class DrawingMode {
    BRUSH,
    ERASER,
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
    val movementLocked: Boolean = false,
    val scaleLocked: Boolean = false,
    val rotationLocked: Boolean = false,
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

class MeasurementPath(points: List<WorldPoint>) {
    val points = mutableStateListOf<WorldPoint>().apply {
        addAll(points)
    }
}

data class DrawingStroke(
    val points: List<WorldPoint>,
    val widthWorldUnits: Double,
    val colorArgb: Long = DEFAULT_DRAWING_COLOR_ARGB,
)

data class TabletopNote(
    val id: Long,
    val position: WorldPoint,
    val text: String,
)

const val DEFAULT_DRAWING_COLOR_ARGB: Long = 0xFF9C3D54L