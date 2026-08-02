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
    fun pointyTopVertexSnappingFindsNearestCorner() {
        val grid = HexGridGeometry(10.0, HexOrientation.POINTY_TOP)
        val expected = grid.corners(WorldPoint.Zero)[2]
        val nearbyPoint = WorldPoint(expected.x + 0.2, expected.y - 0.1)

        val snapped = grid.snapToVertex(nearbyPoint)

        assertEquals(expected.x, snapped.x, absoluteTolerance = 1e-9)
        assertEquals(expected.y, snapped.y, absoluteTolerance = 1e-9)
    }

    @Test
    fun flatTopVertexSnappingFindsNearestCorner() {
        val grid = HexGridGeometry(10.0, HexOrientation.FLAT_TOP)
        val expected = grid.corners(WorldPoint.Zero)[4]
        val nearbyPoint = WorldPoint(expected.x - 0.15, expected.y + 0.1)

        val snapped = grid.snapToVertex(nearbyPoint)

        assertEquals(expected.x, snapped.x, absoluteTolerance = 1e-9)
        assertEquals(expected.y, snapped.y, absoluteTolerance = 1e-9)
    }

    @Test
    fun nearestAnchorChoosesHexCenterWhenItIsCloser() {
        val grid = HexGridGeometry(10.0, HexOrientation.POINTY_TOP)

        val snapped = grid.snapToNearestAnchor(WorldPoint(0.2, -0.1))

        assertEquals(WorldPoint.Zero, snapped)
    }

    @Test
    fun nearestAnchorChoosesHexVertexWhenItIsCloser() {
        val grid = HexGridGeometry(10.0, HexOrientation.FLAT_TOP)
        val expected = grid.corners(WorldPoint.Zero).first()

        val snapped = grid.snapToNearestAnchor(
            WorldPoint(expected.x - 0.1, expected.y + 0.05),
        )

        assertEquals(expected.x, snapped.x, absoluteTolerance = 1e-9)
        assertEquals(expected.y, snapped.y, absoluteTolerance = 1e-9)
    }

    @Test
    fun cubeDistanceCountsHexSteps() {
        val start = AxialCoordinate(0, 0)
        val end = AxialCoordinate(4, -1)

        assertEquals(4, start.distanceTo(end))
    }
}
