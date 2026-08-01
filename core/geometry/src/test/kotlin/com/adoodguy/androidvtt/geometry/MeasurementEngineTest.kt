package com.adoodguy.androidvtt.geometry

import kotlin.test.Test
import kotlin.test.assertEquals

class MeasurementEngineTest {
    @Test
    fun squareMeasurementAppliesArbitraryUnitScale() {
        val result = MeasurementEngine.measureSquare(
            start = WorldPoint(0.0, 0.0),
            end = WorldPoint(3.0, 4.0),
            grid = SquareGridGeometry(cellSize = 1.0),
            unitScale = UnitScale(displayedUnitsPerWorldUnit = 5.0, unitAbbreviation = "ft"),
        )

        assertEquals(5.0, result.worldDistance, absoluteTolerance = 1e-9)
        assertEquals(25.0, result.displayedDistance, absoluteTolerance = 1e-9)
        assertEquals(4, result.gridSteps)
    }
}
