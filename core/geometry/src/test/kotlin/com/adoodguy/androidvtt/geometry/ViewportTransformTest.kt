package com.adoodguy.androidvtt.geometry

import kotlin.test.Test
import kotlin.test.assertEquals

class ViewportTransformTest {
    @Test
    fun screenAndWorldConversionsRoundTrip() {
        val transform = ViewportTransform(
            cameraCenter = WorldPoint(12.0, -8.0),
            pixelsPerWorldUnit = 100.0,
            viewport = ViewportSize(1200.0, 800.0),
        )
        val point = WorldPoint(-3.5, 9.25)

        val roundTripped = transform.screenToWorld(transform.worldToScreen(point))

        assertEquals(point.x, roundTripped.x, absoluteTolerance = 1e-9)
        assertEquals(point.y, roundTripped.y, absoluteTolerance = 1e-9)
    }

    @Test
    fun zoomKeepsAnchorOverSameWorldPoint() {
        val transform = ViewportTransform(
            cameraCenter = WorldPoint.Zero,
            pixelsPerWorldUnit = 80.0,
            viewport = ViewportSize(1000.0, 700.0),
        )
        val anchor = ScreenPoint(730.0, 210.0)
        val before = transform.screenToWorld(anchor)

        val zoomed = transform.zoomAt(anchor, 1.75, 20.0, 400.0)
        val after = zoomed.screenToWorld(anchor)

        assertEquals(before.x, after.x, absoluteTolerance = 1e-9)
        assertEquals(before.y, after.y, absoluteTolerance = 1e-9)
    }
}
