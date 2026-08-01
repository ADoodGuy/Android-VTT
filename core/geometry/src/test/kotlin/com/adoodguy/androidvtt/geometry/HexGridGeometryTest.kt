package com.adoodguy.androidvtt.geometry

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

class HexGridGeometryTest {
    @Test
    fun flatToFlatDefinesInscribedCircleDiameter() {
        val grid = HexGridGeometry(
            flatToFlat = 10.0,
            orientation = HexOrientation.POINTY_TOP,
        )

        assertEquals(5.0, grid.apothem, absoluteTolerance = 1e-9)
        assertEquals(10.0 / sqrt(3.0), grid.circumradius, absoluteTolerance = 1e-9)
    }

    @Test
    fun pointyTopCentersRoundTripThroughWorldCoordinates() {
        val grid = HexGridGeometry(10.0, HexOrientation.POINTY_TOP)
        val coordinate = AxialCoordinate(q = -4, r = 7)

        assertEquals(coordinate, grid.coordinateAt(grid.centerOf(coordinate)))
    }

    @Test
    fun flatTopCentersRoundTripThroughWorldCoordinates() {
        val grid = HexGridGeometry(10.0, HexOrientation.FLAT_TOP)
        val coordinate = AxialCoordinate(q = 6, r = -3)

        assertEquals(coordinate, grid.coordinateAt(grid.centerOf(coordinate)))
    }

    @Test
    fun cubeDistanceCountsHexSteps() {
        val start = AxialCoordinate(0, 0)
        val end = AxialCoordinate(4, -1)

        assertEquals(4, start.distanceTo(end))
    }
}
