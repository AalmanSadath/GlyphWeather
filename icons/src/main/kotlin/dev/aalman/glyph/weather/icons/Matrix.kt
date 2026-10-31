package dev.aalman.glyph.weather.icons

/**
 * Geometry of the Nothing Phone (3) Glyph Matrix: a 25×25 grid where only the cells inside
 * a circle are wired to LEDs. The radius reproduces Nothing's published LED allocation
 * (row widths 7, 11, 15, 17, 19, 21, 21, 23, 23, 25 … mirrored), 489 LEDs in total.
 */
object Matrix {
    const val SIZE = 25
    const val CELLS = SIZE * SIZE

    private const val CENTER = 12
    private const val RADIUS_SQ = 153

    private val mask = BooleanArray(CELLS) { i ->
        val dx = i % SIZE - CENTER
        val dy = i / SIZE - CENTER
        dx * dx + dy * dy <= RADIUS_SQ
    }

    fun isLed(x: Int, y: Int): Boolean =
        x in 0 until SIZE && y in 0 until SIZE && mask[y * SIZE + x]

    val ledCount: Int = mask.count { it }
}
