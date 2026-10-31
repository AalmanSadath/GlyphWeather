package dev.aalman.glyph.weather.icons

import kotlin.math.roundToInt

/** Full-matrix layouts that combine icons and text. */
object Pages {
    private const val ICON_TOP = 1
    private const val TEXT_TOP = 14

    /**
     * Small condition icon in the top half with the temperature, e.g. "23°", centred below it.
     * Animated like the icon; the number stays still.
     */
    fun current(condition: Condition, temperature: Int): Animation {
        val icon = WeatherIcons.small(condition, x = (Matrix.SIZE - WeatherIcons.SMALL_SIZE) / 2, y = ICON_TOP)
        val text = "$temperature°"
        val x = (Matrix.SIZE - PixelFont.width(text)) / 2
        val frames = icon.frames.map { frame ->
            frame.copyOf().also { PixelFont.draw(it, text, x, TEXT_TOP) }
        }
        return Animation(frames, icon.frameMillis, icon.stillIndex)
    }

    /** Today's high above a faint divider, the low below it and dimmer. */
    fun hiLo(high: Int, low: Int): Animation {
        val frame = IntArray(Matrix.CELLS)
        centredText(frame, "$high°", top = 4, brightness = 255)
        for (x in 7..17) frame[12 * Matrix.SIZE + x] = DIVIDER
        centredText(frame, "$low°", top = 14, brightness = LOW_TEXT)
        return Animation(listOf(frame), STATIC_MILLIS)
    }

    /**
     * Chance of rain for the coming hours as bars, one per hour from left to right, starting
     * with the current hour. Hours with no chance show a faint dot on the baseline; faint ticks
     * under the baseline mark every third hour.
     */
    fun rainBar(chances: List<Int>): Animation {
        val frame = IntArray(Matrix.CELLS)
        chances.take(BARS).forEachIndexed { i, chance ->
            val x = 1 + i * 2
            val height = (chance.coerceIn(0, 100) / 100.0 * BAR_MAX).roundToInt()
            if (height == 0) {
                frame[BASELINE * Matrix.SIZE + x] = EMPTY_HOUR
            } else {
                for (y in BASELINE - height + 1..BASELINE) frame[y * Matrix.SIZE + x] = 255
            }
        }
        for (hour in 3 until BARS step 3) frame[(BASELINE + 2) * Matrix.SIZE + 1 + hour * 2] = TICK
        return Animation(listOf(frame), STATIC_MILLIS)
    }

    /** Shown before a city is set or the first forecast arrives. */
    fun unknown(): Animation {
        val frame = IntArray(Matrix.CELLS)
        centredText(frame, "?", top = 9, brightness = 255)
        return Animation(listOf(frame), STATIC_MILLIS)
    }

    private fun centredText(frame: IntArray, text: String, top: Int, brightness: Int) =
        PixelFont.draw(frame, text, (Matrix.SIZE - PixelFont.width(text)) / 2, top, brightness)

    private const val STATIC_MILLIS = 1000
    private const val DIVIDER = 70
    private const val LOW_TEXT = 150

    private const val BARS = 12
    // Columns 1 and 23 (the outermost bars) have LEDs on rows 7–17 only.
    private const val BASELINE = 17
    private const val BAR_MAX = 11
    private const val EMPTY_HOUR = 60
    private const val TICK = 60
}
