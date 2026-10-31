package dev.aalman.glyph.weather.debug

import dev.aalman.glyph.weather.icons.Animation
import dev.aalman.glyph.weather.icons.Condition
import dev.aalman.glyph.weather.icons.Matrix
import dev.aalman.glyph.weather.icons.Pages
import dev.aalman.glyph.weather.icons.WeatherIcons

/** Every icon at both sizes plus the temp page; icons are rendered on first use and cached. */
object IconCatalog {
    private val smallOffset = (Matrix.SIZE - WeatherIcons.SMALL_SIZE) / 2
    private val full = mutableMapOf<Condition, Animation>()
    private val small = mutableMapOf<Condition, Animation>()

    enum class View(val label: String) { FULL("full icon"), SMALL("small icon"), CURRENT("temp page") }

    /** Temperatures to try on the temp page, chosen to cover narrow, wide and negative readings. */
    val sampleTemperatures = listOf(23, -5, 0, -12, 104, 7, -40, 11)

    fun get(condition: Condition, view: View, temperature: Int): Animation = when (view) {
        View.FULL -> full.getOrPut(condition) { WeatherIcons.animation(condition) }
        View.SMALL -> small.getOrPut(condition) { WeatherIcons.small(condition, smallOffset, smallOffset) }
        View.CURRENT -> Pages.current(condition, temperature)
    }

    val sampleHiLo = listOf(23 to 12, 104 to 81, -5 to -18, 0 to -12, 9 to 1, 31 to 19)

    val sampleRain = listOf(
        List(12) { 0 },
        listOf(0, 0, 5, 10, 20, 40, 70, 90, 100, 80, 50, 20),
        listOf(100, 90, 60, 30, 10, 0, 0, 0, 0, 0, 0, 0),
        listOf(10, 30, 10, 60, 20, 80, 40, 100, 30, 50, 10, 0),
        List(12) { 100 },
        List(12) { 35 },
    )

    /** Left-to-right ramp across the full 0–255 brightness range. */
    fun ramp(): IntArray = IntArray(Matrix.CELLS) { i -> (i % Matrix.SIZE) * 255 / (Matrix.SIZE - 1) }
}
