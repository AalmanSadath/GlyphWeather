package dev.aalman.glyph.weather.data

import dev.aalman.glyph.weather.data.source.SourceId
import dev.aalman.glyph.weather.icons.Condition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ForecastTest {
    private val zone = ZoneId.of("Australia/Sydney")
    private val city = City("Sydney", latitude = -33.8688, longitude = 151.2093, timezone = zone.id)
    private val start = Instant.parse("2026-11-01T13:00:00Z").toEpochMilli() // 00:00 AEDT on 2 November

    // 48 hours: temperature = local hour, rain = 10 at 15:00 on day 1, sunny at midday, cloudy otherwise.
    private val forecast = Forecast(
        SourceId.OPEN_METEO, city.key, 0,
        (0 until 48).map { h ->
            Hour(
                epochMillis = start + h * Forecast.HOUR_MILLIS,
                temperature = (h % 24).toDouble(),
                condition = if (h % 24 == 12) Condition.CLEAR_DAY else Condition.CLOUDY,
                precipitation = if (h == 15) 10 else 0,
            )
        },
    )

    @Test
    fun `finds the hour containing a time`() {
        assertEquals(5.0, forecast.hourAt(start + 5 * Forecast.HOUR_MILLIS + 59 * 60_000)!!.temperature)
        assertNull(forecast.hourAt(start - 1))
        assertNull(forecast.hourAt(start + 48 * Forecast.HOUR_MILLIS))
    }

    @Test
    fun `hours from now start at the current hour`() {
        assertEquals(listOf(22.0, 23.0, 0.0), forecast.hoursFrom(start + 22 * Forecast.HOUR_MILLIS + 1).take(3).map { it.temperature })
    }

    @Test
    fun `days are local, from today, with the midday icon`() {
        val days = forecast.days(zone, start + 30 * Forecast.HOUR_MILLIS) // 06:00 on day 2
        assertEquals(listOf(LocalDate.parse("2026-11-03")), days.map { it.date })
        val both = forecast.days(zone, start)
        assertEquals(2, both.size)
        assertEquals(0.0, both[0].low)
        assertEquals(23.0, both[0].high)
        assertEquals(10, both[0].maxPrecipitation)
        assertEquals(Condition.CLEAR_DAY, both[0].condition)
    }

    @Test
    fun `forecasts are only usable for their own source and city`() {
        assertSame(forecast, forecast.usableFor(SourceId.OPEN_METEO, city))
        assertNull(forecast.usableFor(SourceId.TOMORROW_IO, city))
        assertNull(forecast.usableFor(SourceId.OPEN_METEO, city.copy(longitude = 0.0)))
    }
}
