package dev.aalman.glyph.weather.data

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
class PageRenderer {
    // Full-size icons take a moment to draw, so keep the ones already used.
    private val fullIcons = mutableMapOf<Condition, Animation>()

    /** [snapshot] is null before the first forecast arrives (or before a city is chosen). */
    fun render(page: Page, snapshot: WeatherSnapshot?, unit: TemperatureUnit): Animation {
        if (snapshot == null) return Pages.unknown()
        val condition = Conditions.from(snapshot)
        return when (page) {
            Page.CURRENT -> Pages.current(condition, unit.display(snapshot.temperature))
            Page.ANIMATION -> fullIcons.getOrPut(condition) { WeatherIcons.animation(condition) }
            Page.HI_LO -> Pages.hiLo(unit.display(snapshot.todayHigh), unit.display(snapshot.todayLow))
            Page.RAIN -> Pages.rainBar(snapshot.hourlyPrecipitation)
        }
    }
}
