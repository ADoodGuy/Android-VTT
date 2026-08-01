package com.adoodguy.androidvtt.geometry

import kotlin.math.hypot

/** A point in persistent tabletop world space. */
data class WorldPoint(
    val x: Double,
    val y: Double,
) {
    operator fun plus(vector: WorldVector): WorldPoint = WorldPoint(x + vector.x, y + vector.y)
    operator fun minus(other: WorldPoint): WorldVector = WorldVector(x - other.x, y - other.y)

    fun distanceTo(other: WorldPoint): Double = hypot(other.x - x, other.y - y)

    companion object {
        val Zero = WorldPoint(0.0, 0.0)
    }
}

data class WorldVector(
    val x: Double,
    val y: Double,
) {
    operator fun plus(other: WorldVector): WorldVector = WorldVector(x + other.x, y + other.y)
    operator fun minus(other: WorldVector): WorldVector = WorldVector(x - other.x, y - other.y)
    operator fun times(scale: Double): WorldVector = WorldVector(x * scale, y * scale)
    operator fun div(scale: Double): WorldVector = WorldVector(x / scale, y / scale)
}

data class WorldRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    init {
        require(left <= right) { "left must not exceed right" }
        require(top <= bottom) { "top must not exceed bottom" }
    }

    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val center: WorldPoint get() = WorldPoint((left + right) / 2.0, (top + bottom) / 2.0)

    fun expanded(amount: Double): WorldRect = WorldRect(
        left = left - amount,
        top = top - amount,
        right = right + amount,
        bottom = bottom + amount,
    )

    fun contains(point: WorldPoint): Boolean =
        point.x in left..right && point.y in top..bottom
}

data class ScreenPoint(val x: Double, val y: Double)
data class ScreenVector(val x: Double, val y: Double)
data class ViewportSize(val widthPx: Double, val heightPx: Double)

/**
 * Converts between screen pixels and world units. The camera is represented by the world point
 * shown at the center of the viewport and the number of screen pixels per world unit.
 */
data class ViewportTransform(
    val cameraCenter: WorldPoint,
    val pixelsPerWorldUnit: Double,
    val viewport: ViewportSize,
) {
    init {
        require(pixelsPerWorldUnit > 0.0) { "pixelsPerWorldUnit must be positive" }
        require(viewport.widthPx >= 0.0 && viewport.heightPx >= 0.0)
    }

    fun worldToScreen(point: WorldPoint): ScreenPoint = ScreenPoint(
        x = viewport.widthPx / 2.0 + (point.x - cameraCenter.x) * pixelsPerWorldUnit,
        y = viewport.heightPx / 2.0 + (point.y - cameraCenter.y) * pixelsPerWorldUnit,
    )

    fun screenToWorld(point: ScreenPoint): WorldPoint = WorldPoint(
        x = cameraCenter.x + (point.x - viewport.widthPx / 2.0) / pixelsPerWorldUnit,
        y = cameraCenter.y + (point.y - viewport.heightPx / 2.0) / pixelsPerWorldUnit,
    )

    fun visibleWorldRect(): WorldRect {
        val topLeft = screenToWorld(ScreenPoint(0.0, 0.0))
        val bottomRight = screenToWorld(ScreenPoint(viewport.widthPx, viewport.heightPx))
        return WorldRect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
    }

    fun panBy(screenDelta: ScreenVector): ViewportTransform = copy(
        cameraCenter = WorldPoint(
            x = cameraCenter.x - screenDelta.x / pixelsPerWorldUnit,
            y = cameraCenter.y - screenDelta.y / pixelsPerWorldUnit,
        ),
    )

    /** Returns a transform whose zoom changes while [anchor] remains over the same world point. */
    fun zoomAt(
        anchor: ScreenPoint,
        zoomFactor: Double,
        minimumPixelsPerWorldUnit: Double,
        maximumPixelsPerWorldUnit: Double,
    ): ViewportTransform {
        val anchoredWorldPoint = screenToWorld(anchor)
        val newScale = (pixelsPerWorldUnit * zoomFactor)
            .coerceIn(minimumPixelsPerWorldUnit, maximumPixelsPerWorldUnit)
        val viewportCenterX = viewport.widthPx / 2.0
        val viewportCenterY = viewport.heightPx / 2.0
        return copy(
            cameraCenter = WorldPoint(
                x = anchoredWorldPoint.x - (anchor.x - viewportCenterX) / newScale,
                y = anchoredWorldPoint.y - (anchor.y - viewportCenterY) / newScale,
            ),
            pixelsPerWorldUnit = newScale,
        )
    }
}
