package dev.aalman.glyph.weather.icons

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/** A region in continuous design coordinates, where the pixel at (x, y) spans [x, x+1). */
fun interface Shape {
    fun contains(x: Double, y: Double): Boolean

    infix fun or(other: Shape) = Shape { x, y -> contains(x, y) || other.contains(x, y) }
    infix fun minus(other: Shape) = Shape { x, y -> contains(x, y) && !other.contains(x, y) }
}

fun circle(cx: Double, cy: Double, r: Double) = Shape { x, y ->
    val dx = x - cx
    val dy = y - cy
    dx * dx + dy * dy <= r * r
}

/** Every point within [r] of the segment (x1, y1)–(x2, y2): a line with round caps. */
fun capsule(x1: Double, y1: Double, x2: Double, y2: Double, r: Double) = Shape { x, y ->
    val vx = x2 - x1
    val vy = y2 - y1
    val lenSq = vx * vx + vy * vy
    val t = if (lenSq == 0.0) 0.0 else (((x - x1) * vx + (y - y1) * vy) / lenSq).coerceIn(0.0, 1.0)
    hypot(x - (x1 + t * vx), y - (y1 + t * vy)) <= r
}

/**
 * Part of a ring of radius [r] and stroke half-width [w], swept clockwise (screen
 * coordinates, y down) from [startDeg] through [sweepDeg] degrees; 0° points right.
 */
fun arc(cx: Double, cy: Double, r: Double, w: Double, startDeg: Double, sweepDeg: Double) = Shape { x, y ->
    val d = hypot(x - cx, y - cy)
    if (d < r - w || d > r + w) return@Shape false
    val deg = (atan2(y - cy, x - cx) * 180 / PI - startDeg).mod(360.0)
    deg <= sweepDeg
}

fun polygon(vararg points: Pair<Double, Double>) = Shape { x, y ->
    var inside = false
    var j = points.size - 1
    for (i in points.indices) {
        val (xi, yi) = points[i]
        val (xj, yj) = points[j]
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    inside
}
