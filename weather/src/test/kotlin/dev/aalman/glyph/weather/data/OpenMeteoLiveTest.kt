package dev.aalman.glyph.weather.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/** Hits the real API. Run with `OPEN_METEO_LIVE=1 ./gradlew :weather:test`. */
@EnabledIfEnvironmentVariable(named = "OPEN_METEO_LIVE", matches = "1")
class OpenMeteoLiveTest {
    private val client = OpenMeteo()

    @Test
    fun `search then forecast`() {
        val city = client.searchCities("London").first()
        val s = client.forecast(city.latitude, city.longitude)
        println("${city.label}: $s")
        assertEquals(OpenMeteo.HOURS, s.hourlyPrecipitation.size)
        assertTrue(s.todayLow <= s.todayHigh)
        assertTrue(s.temperature in -90.0..60.0)
    }
}
