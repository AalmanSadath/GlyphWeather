package dev.aalman.glyph.weather.data

import dev.aalman.glyph.weather.art.NothingArt
import dev.aalman.glyph.weather.data.source.SourceId
import dev.aalman.glyph.weather.icons.Condition
import dev.aalman.glyph.weather.icons.Pages
import dev.aalman.glyph.weather.icons.WeatherIcons
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PageRendererTest {
    private val snapshot = WeatherSnapshot(
        source = SourceId.OPEN_METEO,
        cityKey = "51.5000,-0.1300",
        fetchedAtMillis = 0,
        temperature = 13.7,
        condition = Condition.RAIN,
        todayHigh = 15.2,
        todayLow = 8.4,
        hourlyPrecipitation = listOf(80, 60, 40, 20, 0, 0, 0, 0, 0, 0, 0, 10),
    )
    private val renderer = PageRenderer(NothingArtSource.None)
    private val withArt = PageRenderer()

    @Test
    fun `every page shows the no-data page without a snapshot`() {
        for (page in Page.entries) {
            assertArrayEquals(Pages.unknown().still, renderer.render(page, null, TemperatureUnit.CELSIUS, IconStyle.ORIGINAL).still)
        }
    }

    @Test
    fun `pages use the snapshot and the chosen unit`() {
        assertArrayEquals(
            Pages.current(Condition.RAIN, 14).still,
            renderer.render(Page.CURRENT, snapshot, TemperatureUnit.CELSIUS, IconStyle.ORIGINAL).still,
        )
        assertArrayEquals(
            Pages.current(Condition.RAIN, 57).still,
            renderer.render(Page.CURRENT, snapshot, TemperatureUnit.FAHRENHEIT, IconStyle.ORIGINAL).still,
        )
        assertArrayEquals(Pages.hiLo(15, 8).still, renderer.render(Page.HI_LO, snapshot, TemperatureUnit.CELSIUS, IconStyle.ORIGINAL).still)
        assertArrayEquals(
            Pages.rainBar(snapshot.hourlyPrecipitation).still,
            renderer.render(Page.RAIN, snapshot, TemperatureUnit.CELSIUS, IconStyle.ORIGINAL).still,
        )
        assertArrayEquals(
            WeatherIcons.animation(Condition.RAIN).still,
            renderer.render(Page.ANIMATION, snapshot, TemperatureUnit.CELSIUS, IconStyle.ORIGINAL).still,
        )
    }

    @Test
    fun `full-size animations are reused`() {
        val first = renderer.render(Page.ANIMATION, snapshot, TemperatureUnit.CELSIUS, IconStyle.ORIGINAL)
        assertSame(first, renderer.render(Page.ANIMATION, snapshot.copy(temperature = 2.0), TemperatureUnit.FAHRENHEIT, IconStyle.ORIGINAL))
    }

    @Test
    fun `nothing style uses the bundled animations`() {
        assertArrayEquals(
            NothingArt.full(Condition.RAIN)!!.still,
            withArt.render(Page.ANIMATION, snapshot, TemperatureUnit.CELSIUS, IconStyle.NOTHING).still,
        )
        val icon = NothingArt.small(Condition.RAIN, Pages.SMALL_ICON_X, Pages.SMALL_ICON_Y)!!
        val current = withArt.render(Page.CURRENT, snapshot, TemperatureUnit.CELSIUS, IconStyle.NOTHING)
        assertArrayEquals(Pages.current(Condition.RAIN, 14, icon).still, current.still)
        assertFalse(current.loop)
    }

    @Test
    fun `original style ignores the bundled animations`() {
        assertArrayEquals(
            WeatherIcons.animation(Condition.RAIN).still,
            withArt.render(Page.ANIMATION, snapshot, TemperatureUnit.CELSIUS, IconStyle.ORIGINAL).still,
        )
    }

    @Test
    fun `without bundled art the nothing style falls back to the original icons`() {
        assertArrayEquals(
            WeatherIcons.animation(Condition.RAIN).still,
            renderer.render(Page.ANIMATION, snapshot, TemperatureUnit.CELSIUS, IconStyle.NOTHING).still,
        )
        assertArrayEquals(
            Pages.current(Condition.RAIN, 14).still,
            renderer.render(Page.CURRENT, snapshot, TemperatureUnit.CELSIUS, IconStyle.NOTHING).still,
        )
    }

    @Test
    fun `extreme temperatures switch the animation page`() {
        fun at(celsius: Double) =
            withArt.render(Page.ANIMATION, snapshot.copy(temperature = celsius), TemperatureUnit.CELSIUS, IconStyle.NOTHING).still
        val heat = NothingArt.extreme(hot = true)!!.still
        val cold = NothingArt.extreme(hot = false)!!.still
        val rain = NothingArt.full(Condition.RAIN)!!.still
        assertArrayEquals(rain, at(39.9))
        assertArrayEquals(heat, at(40.0))
        assertArrayEquals(rain, at(-9.9))
        assertArrayEquals(cold, at(-10.0))
    }
}
