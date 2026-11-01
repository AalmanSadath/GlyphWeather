package dev.aalman.glyph.weather.art

import dev.aalman.glyph.weather.icons.Animation
import dev.aalman.glyph.weather.icons.Condition
import dev.aalman.glyph.weather.icons.Matrix
import dev.aalman.glyph.weather.icons.Pages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NothingArtTest {
    @Test
    fun `art is bundled`() {
        assertTrue(NothingArt.available)
    }

    @Test
    fun `every condition and both extremes load as play-once animations`() {
        val all = Condition.entries.flatMap {
            listOf(NothingArt.full(it), NothingArt.small(it, Pages.SMALL_ICON_X, Pages.SMALL_ICON_Y))
        } + listOf(NothingArt.extreme(hot = true), NothingArt.extreme(hot = false))
        for (animation in all) {
            assertNotNull(animation)
            animation!!
            assertFalse(animation.loop)
            assertEquals(animation.frames.lastIndex, animation.stillIndex)
            assertTrue(animation.frames.all { it.size == Matrix.CELLS })
            assertTrue(animation.still.any { it > 0 }, "last frame is blank")
        }
    }

    @Test
    fun `small icons lose no dots to the round edge`() {
        // At (6, 6) the whole 12×12 square is inside the LED circle, so nothing can be clipped.
        for (condition in Condition.entries) {
            val inSlot = NothingArt.small(condition, Pages.SMALL_ICON_X, Pages.SMALL_ICON_Y)!!
            val centred = NothingArt.small(condition, 6, 6)!!
            assertEquals(lit(centred), lit(inSlot), "$condition is clipped at the top of the matrix")
        }
    }

    @Test
    fun `small icons stay above the temperature`() {
        for (condition in Condition.entries) {
            val small = NothingArt.small(condition, Pages.SMALL_ICON_X, Pages.SMALL_ICON_Y)!!
            for (frame in small.frames) for (i in frame.indices) {
                if (frame[i] > 0) assertTrue(i / Matrix.SIZE < 14, "$condition reaches row ${i / Matrix.SIZE}")
            }
        }
    }

    private fun lit(animation: Animation) = animation.frames.sumOf { f -> f.count { it > 0 } }
}
