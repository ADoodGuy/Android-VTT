package com.adoodguy.androidvtt.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

enum class GridKind {
    SQUARE,
    HEX,
}

enum class HexOrientation {
    POINTY_TOP,
    FLAT_TOP,
}

data class AxialCoordinate(val q: Int, val r: Int) {
    val cubeX: Int get() = q
    val cubeZ: Int get() = r
    val cubeY: Int get() = -q - r

    fun distanceTo(other: AxialCoordinate): Int = (
        abs(cubeX - other.cubeX) +
            abs(cubeY - other.cubeY) +
            abs(cubeZ - other.cubeZ)
        ) / 2
}

data class SquareGridGeometry(
    val cellSize: Double,
    val origin: WorldPoint = WorldPoint.Zero,
) {
    init {
        require(cellSize > 0.0) { "cellSize must be positive" }
    }

    /** Grid intersections are integer multiples of [cellSize] from [origin]. */
    fun snapToIntersection(point: WorldPoint): WorldPoint = WorldPoint(
        x = origin.x + round((point.x - origin.x) / cellSize) * cellSize,
        y = origin.y + round((point.y - origin.y) / cellSize) * cellSize,
    )

    /** Cell centers are half a cell offset from the intersection origin. */
    fun snapToCellCenter(point: WorldPoint): WorldPoint = WorldPoint(
        x = origin.x + (round((point.x - origin.x) / cellSize - 0.5) + 0.5) * cellSize,
        y = origin.y + (round((point.y - origin.y) / cellSize - 0.5) + 0.5) * cellSize,
    )

    fun chebyshevSteps(start: WorldPoint, end: WorldPoint): Int {
        val dx = round(abs(end.x - start.x) / cellSize).toInt()
        val dy = round(abs(end.y - start.y) / cellSize).toInt()
        return max(dx, dy)
    }

    fun verticalLines(bounds: WorldRect): List<Double> {
        val first = floor((bounds.left - origin.x) / cellSize).toInt() - 1
        val last = ceil((bounds.right - origin.x) / cellSize).toInt() + 1
        return (first..last).map { origin.x + it * cellSize }
    }

    fun horizontalLines(bounds: WorldRect): List<Double> {
        val first = floor((bounds.top - origin.y) / cellSize).toInt() - 1
        val last = ceil((bounds.bottom - origin.y) / cellSize).toInt() + 1
        return (first..last).map { origin.y + it * cellSize }
    }
}

/**
 * Regular hex geometry where [flatToFlat] is the perpendicular distance between opposing edges.
 * A circle whose diameter equals [flatToFlat] is therefore inscribed in one hex.
 */
data class HexGridGeometry(
    val flatToFlat: Double,
    val orientation: HexOrientation,
    val origin: WorldPoint = WorldPoint.Zero,
) {
    init {
        require(flatToFlat > 0.0) { "flatToFlat must be positive" }
    }

    val apothem: Double get() = flatToFlat / 2.0
    val circumradius: Double get() = flatToFlat / SQRT_THREE
    val cornerToCorner: Double get() = 2.0 * circumradius

    fun centerOf(coordinate: AxialCoordinate): WorldPoint {
        val q = coordinate.q.toDouble()
        val r = coordinate.r.toDouble()
        return when (orientation) {
            HexOrientation.POINTY_TOP -> WorldPoint(
                x = origin.x + flatToFlat * (q + r / 2.0),
                y = origin.y + flatToFlat * SQRT_THREE / 2.0 * r,
            )

            HexOrientation.FLAT_TOP -> WorldPoint(
                x = origin.x + flatToFlat * SQRT_THREE / 2.0 * q,
                y = origin.y + flatToFlat * (r + q / 2.0),
            )
        }
    }

    fun coordinateAt(point: WorldPoint): AxialCoordinate {
        val localX = point.x - origin.x
        val localY = point.y - origin.y
        val fractional = when (orientation) {
            HexOrientation.POINTY_TOP -> FractionalAxial(
                q = localX / flatToFlat - localY / (SQRT_THREE * flatToFlat),
                r = 2.0 * localY / (SQRT_THREE * flatToFlat),
            )

            HexOrientation.FLAT_TOP -> FractionalAxial(
                q = 2.0 * localX / (SQRT_THREE * flatToFlat),
                r = localY / flatToFlat - localX / (SQRT_THREE * flatToFlat),
            )
        }
        return fractional.rounded()
    }

    fun snapToCenter(point: WorldPoint): WorldPoint = centerOf(coordinateAt(point))

    fun distanceSteps(start: WorldPoint, end: WorldPoint): Int =
        coordinateAt(start).distanceTo(coordinateAt(end))

    fun corners(center: WorldPoint): List<WorldPoint> {
        val angleOffsetDegrees = when (orientation) {
            HexOrientation.POINTY_TOP -> -90.0
            HexOrientation.FLAT_TOP -> 0.0
        }
        return List(6) { index ->
            val radians = (angleOffsetDegrees + index * 60.0) * PI / 180.0
            WorldPoint(
                x = center.x + circumradius * cos(radians),
                y = center.y + circumradius * sin(radians),
            )
        }
    }

    /** Returns enough cells to cover [bounds], including a two-cell rendering margin. */
    fun visibleCells(bounds: WorldRect): List<AxialCoordinate> {
        val corners = listOf(
            WorldPoint(bounds.left, bounds.top),
            WorldPoint(bounds.right, bounds.top),
            WorldPoint(bounds.left, bounds.bottom),
            WorldPoint(bounds.right, bounds.bottom),
        ).map(::fractionalCoordinateAt)

        val minimumQ = floor(corners.minOf { it.q }).toInt() - 2
        val maximumQ = ceil(corners.maxOf { it.q }).toInt() + 2
        val minimumR = floor(corners.minOf { it.r }).toInt() - 2
        val maximumR = ceil(corners.maxOf { it.r }).toInt() + 2
        val expandedBounds = bounds.expanded(cornerToCorner)

        val result = ArrayList<AxialCoordinate>((maximumQ - minimumQ + 1) * (maximumR - minimumR + 1))
        for (q in minimumQ..maximumQ) {
            for (r in minimumR..maximumR) {
                val coordinate = AxialCoordinate(q, r)
                if (expandedBounds.contains(centerOf(coordinate))) {
                    result += coordinate
                }
            }
        }
        return result
    }

    private fun fractionalCoordinateAt(point: WorldPoint): FractionalAxial {
        val localX = point.x - origin.x
        val localY = point.y - origin.y
        return when (orientation) {
            HexOrientation.POINTY_TOP -> FractionalAxial(
                q = localX / flatToFlat - localY / (SQRT_THREE * flatToFlat),
                r = 2.0 * localY / (SQRT_THREE * flatToFlat),
            )

            HexOrientation.FLAT_TOP -> FractionalAxial(
                q = 2.0 * localX / (SQRT_THREE * flatToFlat),
                r = localY / flatToFlat - localX / (SQRT_THREE * flatToFlat),
            )
        }
    }

    private data class FractionalAxial(val q: Double, val r: Double) {
        fun rounded(): AxialCoordinate {
            val x = q
            val z = r
            val y = -x - z

            var roundedX = round(x)
            val roundedY = round(y)
            var roundedZ = round(z)

            val xDifference = abs(roundedX - x)
            val yDifference = abs(roundedY - y)
            val zDifference = abs(roundedZ - z)

            when {
                xDifference > yDifference && xDifference > zDifference -> roundedX = -roundedY - roundedZ
                yDifference > zDifference -> Unit // q and r are already the nearest valid pair.
                else -> roundedZ = -roundedX - roundedY
            }

            return AxialCoordinate(roundedX.toInt(), roundedZ.toInt())
        }
    }

    private companion object {
        val SQRT_THREE: Double = sqrt(3.0)
    }
}
