package dev.aalman.glyph.weather.icons

import kotlin.math.floor
import kotlin.math.roundToInt

/** A 25×25 brightness buffer (0–255 per LED) with anti-aliased shape drawing. */
class Canvas(val pixels: IntArray = IntArray(Matrix.CELLS)) {
    /** Lightens each LED towards [brightness] in proportion to how much of it [shape] covers. */
    fun fill(brightness: Int, shape: Shape) = forEachCovered(shape) { i, coverage ->
        pixels[i] = maxOf(pixels[i], (brightness * coverage).roundToInt())
    }

    /** Darkens each LED in proportion to how much of it [shape] covers. */
    fun erase(shape: Shape) = forEachCovered(shape) { i, coverage ->
        pixels[i] = (pixels[i] * (1 - coverage)).roundToInt()
    }

    /** Lights the single LED under point (x, y); for stars, flakes and drizzle. */
    fun dot(x: Double, y: Double, brightness: Int) {
        val px = floor(x).toInt()
        val py = floor(y).toInt()
        if (px !in 0 until Matrix.SIZE || py !in 0 until Matrix.SIZE) return
        val i = py * Matrix.SIZE + px
        pixels[i] = maxOf(pixels[i], brightness.coerceIn(0, 255))
    }

    /** Copy of the buffer with every cell outside the round LED area forced to zero. */
    fun snapshot(): IntArray = IntArray(Matrix.CELLS) { i ->
        if (Matrix.isLed(i % Matrix.SIZE, i / Matrix.SIZE)) pixels[i] else 0
    }

    private inline fun forEachCovered(shape: Shape, apply: (index: Int, coverage: Double) -> Unit) {
        for (py in 0 until Matrix.SIZE) for (px in 0 until Matrix.SIZE) {
            var hits = 0
            for (sy in 0 until SAMPLES) for (sx in 0 until SAMPLES) {
                if (shape.contains(px + (sx + 0.5) / SAMPLES, py + (sy + 0.5) / SAMPLES)) hits++
            }
            if (hits > 0) apply(py * Matrix.SIZE + px, hits.toDouble() / (SAMPLES * SAMPLES))
        }
    }

    private companion object {
        const val SAMPLES = 4
    }
}
