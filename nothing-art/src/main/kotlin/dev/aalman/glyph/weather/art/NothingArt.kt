package dev.aalman.glyph.weather.art

import dev.aalman.glyph.weather.icons.Animation
import dev.aalman.glyph.weather.icons.Condition
import dev.aalman.glyph.weather.icons.Matrix
import java.io.DataInputStream

/**
 * Nothing Weather's widget animations, converted to the Glyph Matrix by `tools/convert.py`.
 *
 * Each one plays once and ends on a still image. Every lookup returns null when the files are
 * missing, so deleting `src/main/resources/nothing-art/` leaves the app on its own icons.
 */
object NothingArt {
    private const val ROOT = "/nothing-art"

    private val fileNames = mapOf(
        Condition.CLEAR_DAY to "clearday",
        Condition.CLEAR_NIGHT to "clearnight",
        Condition.PARTLY_CLOUDY_DAY to "partlycloudy",
        Condition.PARTLY_CLOUDY_NIGHT to "cloudynight",
        Condition.CLOUDY to "overcast",
        Condition.FOG to "fog",
        Condition.DRIZZLE to "rainy",
        Condition.RAIN to "rainy",
        Condition.SLEET to "sleet",
        Condition.SNOW to "snow",
        Condition.THUNDERSTORM to "thunderstorm",
        Condition.WINDY to "windy",
    )

    private val cache = mutableMapOf<String, Frames?>()

    /** True when the converted files are bundled. */
    val available: Boolean by lazy { javaClass.getResource("$ROOT/full/clearday.gwf") != null }

    /** Full-matrix animation for [condition]. */
    fun full(condition: Condition): Animation? =
        fileNames[condition]?.let { load("full/$it") }?.toMatrix(0, 0)

    /** 12×12 animation for [condition], placed with its top-left corner at ([x], [y]). */
    fun small(condition: Condition, x: Int, y: Int): Animation? =
        fileNames[condition]?.let { load("small/$it") }?.toMatrix(x, y)

    /** Full-matrix animation for extreme heat ([hot]) or extreme cold. */
    fun extreme(hot: Boolean): Animation? =
        load(if (hot) "full/extremeheat" else "full/extremecold")?.toMatrix(0, 0)

    @Synchronized
    private fun load(path: String): Frames? = cache.getOrPut(path) {
        javaClass.getResourceAsStream("$ROOT/$path.gwf")?.let { stream ->
            DataInputStream(stream.buffered()).use(::readGwf)
        }
    }

    private class Frames(val width: Int, val height: Int, val frameMillis: Int, val frames: List<ByteArray>) {
        fun toMatrix(x: Int, y: Int): Animation {
            val matrixFrames = frames.map { bytes ->
                IntArray(Matrix.CELLS).also { out ->
                    for (row in 0 until height) for (col in 0 until width) {
                        val v = bytes[row * width + col].toInt() and 0xFF
                        if (v > 0 && Matrix.isLed(x + col, y + row)) out[(y + row) * Matrix.SIZE + x + col] = v
                    }
                }
            }
            return Animation(matrixFrames, frameMillis, stillIndex = matrixFrames.lastIndex, loop = false)
        }
    }

    /** `GWF2`, u8 width, u8 height, u16 frame count, u16 frame ms, then width×height bytes per frame. */
    private fun readGwf(input: DataInputStream): Frames {
        val magic = ByteArray(4).also(input::readFully)
        require(magic.decodeToString() == "GWF2") { "not a GWF2 file" }
        val width = input.readUnsignedByte()
        val height = input.readUnsignedByte()
        val count = input.readUnsignedShort()
        val millis = input.readUnsignedShort()
        val frames = List(count) { ByteArray(width * height).also(input::readFully) }
        return Frames(width, height, millis, frames)
    }
}
