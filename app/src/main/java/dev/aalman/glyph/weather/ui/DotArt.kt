package dev.aalman.glyph.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.aalman.glyph.weather.art.NothingArt
import dev.aalman.glyph.weather.data.IconStyle
import dev.aalman.glyph.weather.data.PageRenderer
import dev.aalman.glyph.weather.icons.Animation
import dev.aalman.glyph.weather.icons.Condition
import dev.aalman.glyph.weather.icons.Matrix
import dev.aalman.glyph.weather.icons.WeatherIcons
import kotlinx.coroutines.delay

/** Unlit LEDs, as in Nothing's Glyph preview image specification. */
val UNLIT = Color(0xFF1C1C1C)

/**
 * Draws part of a 25×25 matrix frame as round dots: the [size]×[size] square whose top-left
 * cell is ([left], [top]). Brightness 0–255 maps to grey. With [showUnlit], LED positions that
 * are off are drawn in [UNLIT], so the round matrix shows.
 */
@Composable
fun DotFrame(
    frame: IntArray,
    modifier: Modifier = Modifier,
    left: Int = 0,
    top: Int = 0,
    size: Int = Matrix.SIZE,
    showUnlit: Boolean = false,
) {
    Canvas(modifier) {
        val pitch = minOf(this.size.width, this.size.height) / size
        val radius = pitch * 0.42f
        for (row in 0 until size) for (col in 0 until size) {
            val x = left + col
            val y = top + row
            val v = if (x in 0 until Matrix.SIZE && y in 0 until Matrix.SIZE) frame[y * Matrix.SIZE + x] else 0
            val colour = when {
                v > 0 -> Color(v, v, v)
                showUnlit && Matrix.isLed(x, y) -> UNLIT
                else -> continue
            }
            drawCircle(colour, radius, Offset((col + 0.5f) * pitch, (row + 0.5f) * pitch))
        }
    }
}

/**
 * The frame of [animation] to draw now. Plays it when [animate] is on: looped, or once and then
 * held on the last frame for play-once animations. Shows the still frame otherwise.
 */
@Composable
fun animatedFrame(animation: Animation, animate: Boolean): IntArray {
    var index by remember(animation, animate) { mutableIntStateOf(if (animate) 0 else animation.stillIndex) }
    LaunchedEffect(animation, animate) {
        if (!animate || animation.frames.size < 2) return@LaunchedEffect
        while (true) {
            delay(animation.frameMillis.toLong())
            if (index == animation.frames.lastIndex) {
                if (!animation.loop) break
                index = 0
            } else {
                index++
            }
        }
    }
    return animation.frames[index.coerceIn(animation.frames.indices)]
}

/** Weather art for the app's screens, following the Icons setting like the Glyph Toy does. */
object IconArt {
    /** Top-left cell of the 12×12 small icons when drawn for the screen, clear of the round edge. */
    const val SMALL_AT = 6

    private val full = mutableMapOf<Condition, Animation>()

    fun full(condition: Condition, style: IconStyle, celsius: Double?): Animation {
        if (style == IconStyle.NOTHING && NothingArt.available) {
            val extreme = when {
                celsius == null -> null
                celsius >= PageRenderer.EXTREME_HEAT_C -> NothingArt.extreme(hot = true)
                celsius <= PageRenderer.EXTREME_COLD_C -> NothingArt.extreme(hot = false)
                else -> null
            }
            (extreme ?: NothingArt.full(condition))?.let { return it }
        }
        return synchronized(full) { full.getOrPut(condition) { WeatherIcons.animation(condition) } }
    }

    /** The small icon's still frame, at ([SMALL_AT], [SMALL_AT]) in a 25×25 frame. */
    fun small(condition: Condition, style: IconStyle): IntArray {
        val nothing = if (style == IconStyle.NOTHING) NothingArt.small(condition, SMALL_AT, SMALL_AT) else null
        return (nothing ?: WeatherIcons.small(condition, SMALL_AT, SMALL_AT)).still
    }
}

/** A small weather icon drawn as dots. */
@Composable
fun SmallIcon(condition: Condition, style: IconStyle, modifier: Modifier = Modifier) {
    val frame = remember(condition, style) { IconArt.small(condition, style) }
    DotFrame(frame, modifier, left = IconArt.SMALL_AT, top = IconArt.SMALL_AT, size = WeatherIcons.SMALL_SIZE)
}
