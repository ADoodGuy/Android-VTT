package com.adoodguy.androidvtt.geometry

import kotlin.math.round

data class UnitScale(
    val displayedUnitsPerWorldUnit: Double,
    val unitAbbreviation: String,
) {
    init {
        require(displayedUnitsPerWorldUnit > 0.0)
        require(unitAbbreviation.isNotBlank())
    }

    fun displayDistance(worldDistance: Double): Double = worldDistance * displayedUnitsPerWorldUnit
}

data class MeasurementResult(
    val worldDistance: Double,
    val displayedDistance: Double,
    val gridSteps: Int,
    val unitAbbreviation: String,
) {
    fun formatted(): String {
        val distanceText = if (displayedDistance == round(displayedDistance)) {
            displayedDistance.toInt().toString()
        } else {
            "%.1f".format(displayedDistance)
        }
        return "$distanceText $unitAbbreviation • $gridSteps cells"
    }
}

object MeasurementEngine {
    fun measureSquare(
        start: WorldPoint,
        end: WorldPoint,
        grid: SquareGridGeometry,
        unitScale: UnitScale,
    ): MeasurementResult {
        val worldDistance = start.distanceTo(end)
        return MeasurementResult(
            worldDistance = worldDistance,
            displayedDistance = unitScale.displayDistance(worldDistance),
            gridSteps = grid.chebyshevSteps(start, end),
            unitAbbreviation = unitScale.unitAbbreviation,
        )
    }

    fun measureHex(
        start: WorldPoint,
        end: WorldPoint,
        grid: HexGridGeometry,
        unitScale: UnitScale,
    ): MeasurementResult {
        val worldDistance = start.distanceTo(end)
        return MeasurementResult(
            worldDistance = worldDistance,
            displayedDistance = unitScale.displayDistance(worldDistance),
            gridSteps = grid.distanceSteps(start, end),
            unitAbbreviation = unitScale.unitAbbreviation,
        )
    }
}
