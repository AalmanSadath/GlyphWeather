package dev.aalman.glyph.weather.icons

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeatherIconsTest {
    @Test
    fun `mask matches Phone 3 LED allocation`() {
        assertEquals(489, Matrix.ledCount)
        val rowWidths = (0 until Matrix.SIZE).map { y -> (0 until Matrix.SIZE).count { x -> Matrix.isLed(x, y) } }
        assertEquals(listOf(7, 11, 15, 17, 19, 21, 21, 23, 23, 25, 25, 25, 25, 25, 25, 25, 23, 23, 21, 21, 19, 17, 15, 11, 7), rowWidths)
    }

    @Test
    fun `every condition renders lit frames inside the circle`() {
        for (c in Condition.entries) {
            assertValid(c, WeatherIcons.animation(c))
            assertValid(c, WeatherIcons.small(c, x = 6, y = 6))
        }
    }

    @Test
    fun `every plausible temperature fits inside the circle`() {
        for (t in -40..120) {
            val text = "$t°"
            val x = (Matrix.SIZE - PixelFont.width(text)) / 2
            // Rows 9–15 are at least 23 LEDs wide, so text drawn there is never clipped.
            assertTrue(PixelFont.width(text) <= 23)
            val frame = IntArray(Matrix.CELLS)
            PixelFont.draw(frame, text, x, 9)
            val clipped = IntArray(Matrix.CELLS)
            PixelFont.draw(clipped, text, x, 14)
            assertEquals(frame.count { it > 0 }, clipped.count { it > 0 }, "\"$text\" is clipped by the circle")
        }
    }

    @Test
    fun `thunderstorm still frame shows lightning`() {
        for (anim in listOf(WeatherIcons.animation(Condition.THUNDERSTORM), WeatherIcons.small(Condition.THUNDERSTORM, 6, 6))) {
            // The bolt (and the brightened cloud) light more LEDs than the dark frame 0.
            assertTrue(anim.still.count { it == 255 } > anim.frames[0].count { it == 255 })
        }
    }

    @Test
    fun `current page renders for every condition`() {
        for (c in Condition.entries) assertValid(c, Pages.current(c, -12))
    }

    @Test
    fun `hi-lo page fits extreme temperatures`() {
        for ((high, low) in listOf(104 to -40, 23 to 12, 0 to -12, -5 to -18)) {
            val page = Pages.hiLo(high, low)
            assertValid(Condition.CLEAR_DAY, page)
            // Rows 9–15 span the full width, so text drawn there shows how many pixels it should have.
            val unclipped = IntArray(Matrix.CELLS).also { PixelFont.draw(it, "$high°", 1, 9) }.count { it > 0 } +
                IntArray(Matrix.CELLS).also { PixelFont.draw(it, "$low°", 1, 9) }.count { it > 0 }
            val textPixels = page.still.withIndex().count { (i, b) -> b > 0 && i / Matrix.SIZE != 12 }
            assertEquals(unclipped, textPixels, "$high/$low is clipped by the circle")
        }
    }

    @Test
    fun `rain bars are never clipped and never blank`() {
        val full = Pages.rainBar(List(12) { 100 }).still
        assertEquals(12 * 11, full.count { it == 255 }, "a 100% bar is clipped by the circle")
        val dry = Pages.rainBar(List(12) { 0 }).still
        assertTrue(dry.count { it > 0 } >= 12, "a dry forecast shows nothing")
        assertValid(Condition.RAIN, Pages.rainBar(listOf(0, 10, 50, 100, 30, 0, 0, 80, 100, 5, 0, 60)))
    }

    @Test
    fun `unknown page shows something`() = assertValid(Condition.CLOUDY, Pages.unknown())

    private fun assertValid(c: Condition, anim: Animation) {
        assertTrue(anim.frames.isNotEmpty(), "$c has no frames")
        for (frame in anim.frames) {
            assertEquals(Matrix.CELLS, frame.size)
            assertTrue(frame.all { it in 0..255 }, "$c has out-of-range brightness")
            frame.forEachIndexed { i, b ->
                if (b > 0) assertTrue(Matrix.isLed(i % Matrix.SIZE, i / Matrix.SIZE), "$c lights a non-LED cell")
            }
        }
        assertTrue(anim.frames.any { f -> f.any { it > 0 } }, "$c is blank")
    }
}
