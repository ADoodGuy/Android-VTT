package com.adoodguy.androidvtt.geometry

import kotlin.test.Test
import kotlin.test.assertEquals

class SquareGridAnchorTest {
    @Test
    fun horizontalEdgeMidpointSnappingFindsNearestEdgeCenter() {
        val grid = SquareGridGeometry(cellSize = 10.0)
        val expected = WorldPoint(5.0, 10.0)

        val snapped = grid.snapToEdgeMidpoint(WorldPoint(5.12, 9.91))

        assertEquals(expected.x, snapped.x, absoluteTolerance = 1e-9)
        assertEquals(expected.y, snapped.y, absoluteTolerance = 1e-9)
    }

    @Test
    fun verticalEdgeMidpointSnappingFindsNearestEdgeCenter() {
        val grid = SquareGridGeometry(cellSize = 10.0)
        val expected = WorldPoint(20.0, -5.0)

        val snapped = grid.snapToEdgeMidpoint(WorldPoint(19.89, -4.86))

        assertEquals(expected.x, snapped.x, absoluteTolerance = 1e-9)
        assertEquals(expected.y, snapped.y, absoluteTolerance = 1e-9)
    }

    @Test
    fun nearestAnchorChoosesCellCenterWhenItIsCloser() {
        val grid = SquareGridGeometry(cellSize = 10.0)

        val snapped = grid.snapToNearestAnchor(WorldPoint(5.1, 4.9))

        assertEquals(WorldPoint(5.0, 5.0), snapped)
    }

    @Test
    fun nearestAnchorChoosesEdgeMidpointWhenItIsCloser() {
        val grid = SquareGridGeometry(cellSize = 10.0)

        val snapped = grid.snapToNearestAnchor(WorldPoint(5.1, 0.08))

        assertEquals(WorldPoint(5.0, 0.0), snapped)
    }

    @Test
    fun nearestAnchorChoosesIntersectionWhenItIsCloser() {
        val grid = SquareGridGeometry(cellSize = 10.0)

        val snapped = grid.snapToNearestAnchor(WorldPoint(0.08, -0.12))

        assertEquals(WorldPoint.Zero, snapped)
    }
}
