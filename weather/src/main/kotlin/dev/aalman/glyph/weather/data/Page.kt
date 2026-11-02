package dev.aalman.glyph.weather.data

import dev.aalman.glyph.weather.art.NothingArt
import dev.aalman.glyph.weather.icons.Animation
import dev.aalman.glyph.weather.icons.Condition
import dev.aalman.glyph.weather.icons.Pages
import dev.aalman.glyph.weather.icons.WeatherIcons

/** The screens the toy can show; the user picks which ones and in what order. */
enum class Page {
    /** Small condition icon with the current temperature. */
    CURRENT,

    /** Full-screen condition animation. */
    ANIMATION,

    /** Today's high and low. */
    HI_LO,

    /** Chance of rain over the next hours. */
    RAIN;

    companion object {
        val DEFAULT_ORDER = listOf(CURRENT, ANIMATION, HI_LO, RAIN)
    }
}

/** Turns weather data into what a page shows on the matrix. */
class PageRenderer(private val art: NothingArtSource = NothingArtSource.Bundled) {
    // Full-size icons take a moment to draw, so keep the ones already used.
    private val fullIcons = mutableMapOf<Condition, Animation>()

    /** [snapshot] is null before the first forecast arrives (or before a city is chosen). */
    fun render(page: Page, snapshot: WeatherSnapshot?, unit: TemperatureUnit, style: IconStyle): Animation {
        if (snapshot == null) return Pages.unknown()
        val condition = snapshot.condition
        val nothing = style == IconStyle.NOTHING && art.available
        return when (page) {
            Page.CURRENT -> {
                val icon = if (nothing) art.small(condition, Pages.SMALL_ICON_X, Pages.SMALL_ICON_Y) else null
                if (icon != null) Pages.current(condition, unit.display(snapshot.temperature), icon)
                else Pages.current(condition, unit.display(snapshot.temperature))
            }
            Page.ANIMATION -> (if (nothing) nothingAnimation(condition, snapshot.temperature) else null)
                ?: fullIcons.getOrPut(condition) { WeatherIcons.animation(condition) }
            Page.HI_LO -> Pages.hiLo(unit.display(snapshot.todayHigh), unit.display(snapshot.todayLow))
            Page.RAIN -> Pages.rainBar(snapshot.hourlyPrecipitation)
        }
    }

    private fun nothingAnimation(condition: Condition, celsius: Double): Animation? = when {
        celsius >= EXTREME_HEAT_C -> art.extreme(hot = true)
        celsius <= EXTREME_COLD_C -> art.extreme(hot = false)
        else -> null
    } ?: art.full(condition)

    companion object {
        /** At or above this temperature the Animation page shows Nothing's extreme-heat animation. */
        const val EXTREME_HEAT_C = 40.0

        /** At or below this temperature the Animation page shows Nothing's extreme-cold animation. */
        const val EXTREME_COLD_C = -10.0
    }
}

/** Where Nothing's animations come from; tests swap in a source with no art. */
interface NothingArtSource {
    val available: Boolean
    fun full(condition: Condition): Animation?
    fun small(condition: Condition, x: Int, y: Int): Animation?
    fun extreme(hot: Boolean): Animation?

    object Bundled : NothingArtSource {
        override val available get() = NothingArt.available
        override fun full(condition: Condition) = NothingArt.full(condition)
        override fun small(condition: Condition, x: Int, y: Int) = NothingArt.small(condition, x, y)
        override fun extreme(hot: Boolean) = NothingArt.extreme(hot)
    }

    object None : NothingArtSource {
        override val available = false
        override fun full(condition: Condition): Animation? = null
        override fun small(condition: Condition, x: Int, y: Int): Animation? = null
        override fun extreme(hot: Boolean): Animation? = null
    }
}
