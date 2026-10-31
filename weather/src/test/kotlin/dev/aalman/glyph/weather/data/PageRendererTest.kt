package dev.aalman.glyph.weather.data

import dev.aalman.glyph.weather.icons.Condition
import dev.aalman.glyph.weather.icons.Pages
import dev.aalman.glyph.weather.icons.WeatherIcons
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PageRendererTest {
    private val snapshot = WeatherSnapshot(
        fetchedAtMillis = 0,
        temperature = 13.7,
        weatherCode = 63,
        isDay = true,
        windKmh = 12.0,
        todayHigh = 15.2,
        todayLow = 8.4,
        hourlyPrecipitation = listOf(80, 60, 40, 20, 0, 0, 0, 0, 0, 0, 0, 10),
    )
    private val renderer = PageRenderer()

    @Test
    fun `every page shows the no-data page without a snapshot`() {
        for (page in Page.entries) {
            assertArrayEquals(Pages.unknown().still, renderer.render(page, null, TemperatureUnit.CELSIUS).still)
        }
    }

    @Test
    fun `pages use the snapshot and the chosen unit`() {
        assertArrayEquals(
            Pages.current(Condition.RAIN, 14).still,
            renderer.render(Page.CURRENT, snapshot, TemperatureUnit.CELSIUS).still,
        )
        assertArrayEquals(
            Pages.current(Condition.RAIN, 57).still,
            renderer.render(Page.CURRENT, snapshot, TemperatureUnit.FAHRENHEIT).still,
        )
        assertArrayEquals(Pages.hiLo(15, 8).still, renderer.render(Page.HI_LO, snapshot, TemperatureUnit.CELSIUS).still)
        assertArrayEquals(
            Pages.rainBar(snapshot.hourlyPrecipitation).still,
            renderer.render(Page.RAIN, snapshot, TemperatureUnit.CELSIUS).still,
        )
        assertArrayEquals(
            WeatherIcons.animation(Condition.RAIN).still,
            renderer.render(Page.ANIMATION, snapshot, TemperatureUnit.CELSIUS).still,
        )
    }

    @Test
    fun `full-size animations are reused`() {
        val first = renderer.render(Page.ANIMATION, snapshot, TemperatureUnit.CELSIUS)
        assertSame(first, renderer.render(Page.ANIMATION, snapshot.copy(temperature = 2.0), TemperatureUnit.FAHRENHEIT))
    }
}
