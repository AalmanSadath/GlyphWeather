package dev.aalman.glyph.weather.icons

/** Weather conditions that have their own icon. */
enum class Condition {
    CLEAR_DAY,
    CLEAR_NIGHT,
    PARTLY_CLOUDY_DAY,
    PARTLY_CLOUDY_NIGHT,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SLEET,
    SNOW,
    THUNDERSTORM,
    WINDY,
}

/**
 * A sequence of 25×25 brightness frames. [stillIndex] picks the frame shown when animation is
 * turned off, so an icon whose key feature only appears mid-loop (lightning) still shows it.
 * With [loop] false the sequence plays once and stays on its last frame.
 */
class Animation(
    val frames: List<IntArray>,
    val frameMillis: Int,
    val stillIndex: Int = 0,
    val loop: Boolean = true,
) {
    init {
        require(stillIndex in frames.indices) { "stillIndex $stillIndex outside ${frames.size} frames" }
    }

    val still: IntArray get() = frames[stillIndex]
    val durationMillis: Int get() = frames.size * frameMillis
}
