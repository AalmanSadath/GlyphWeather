package dev.aalman.glyph.weather.data

import dev.aalman.glyph.weather.icons.Condition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenMeteoTest {
    private fun fixture(name: String) = javaClass.getResource("/$name")!!.readText()

    @Test
    fun `parses a real forecast response`() {
        val body = fixture("forecast_london.json")
        val s = OpenMeteo.parseForecast(body, nowMillis = 42)

        assertEquals(42, s.fetchedAtMillis)
        assertEquals(13.7, s.temperature)
        assertEquals(1, s.weatherCode)
        assertTrue(s.isDay)
        assertEquals(10.4, s.windKmh)
        assertEquals(14.2, s.todayHigh)
        assertEquals(9.6, s.todayLow)

        // current.time is 12:30, so the slice starts at the 12:00 entry.
        val all = Json.parseToJsonElement(body).jsonObject["hourly"]!!.jsonObject["precipitation_probability"]!!
            .jsonArray.map { it.jsonPrimitive.int }
        assertEquals(all.subList(12, 24), s.hourlyPrecipitation)
    }

    @Test
    fun `hourly slice crosses midnight and treats missing values as zero`() {
        val times = (0 until 48).joinToString(",") { h ->
            "\"2026-10-${31 + h / 24}T%02d:00\"".format(h % 24).replace("10-32", "11-01")
        }
        val chances = (0 until 48).joinToString(",") { h -> if (h == 25) "null" else "$h" }
        val body = """
            {"current":{"time":"2026-10-31T23:45","temperature_2m":-3.2,"weather_code":71,"is_day":0,"wind_speed_10m":5.0},
             "hourly":{"time":[$times],"precipitation_probability":[$chances]},
             "daily":{"temperature_2m_max":[1.0,2.0],"temperature_2m_min":[-5.0,-4.0]}}
        """.trimIndent()

        val s = OpenMeteo.parseForecast(body, nowMillis = 0)

        assertEquals(listOf(23, 24, 0, 26, 27, 28, 29, 30, 31, 32, 33, 34), s.hourlyPrecipitation)
        assertFalse(s.isDay)
    }

    @Test
    fun `parses geocoding results`() {
        val cities = OpenMeteo.parseCities(fixture("geocoding_berlin.json"))
        val berlin = cities.first()
        assertEquals("Berlin", berlin.name)
        assertEquals("Germany", berlin.country)
        assertEquals("Berlin, State of Berlin, Germany", berlin.label)
        assertEquals(52.52437, berlin.latitude)
    }

    @Test
    fun `geocoding with no match returns an empty list`() {
        assertEquals(emptyList<City>(), OpenMeteo.parseCities(fixture("geocoding_none.json")))
    }

    @Test
    fun `blank search makes no request`() {
        val client = OpenMeteo { error("should not be called") }
        assertEquals(emptyList<City>(), client.searchCities("  "))
    }

    @Test
    fun `forecast request asks for the fields the parser reads`() {
        var requested = ""
        OpenMeteo { url -> requested = url; fixture("forecast_london.json") }.forecast(51.5, -0.13)
        for (field in listOf("temperature_2m", "weather_code", "is_day", "wind_speed_10m", "precipitation_probability",
            "temperature_2m_max", "temperature_2m_min")) {
            assertTrue(field in requested, "missing $field")
        }
    }
}

class ConditionsTest {
    @Test
    fun `maps WMO codes`() {
        val day = mapOf(
            0 to Condition.CLEAR_DAY, 1 to Condition.CLEAR_DAY, 2 to Condition.PARTLY_CLOUDY_DAY,
            3 to Condition.CLOUDY, 45 to Condition.FOG, 48 to Condition.FOG,
            51 to Condition.DRIZZLE, 55 to Condition.DRIZZLE, 56 to Condition.SLEET, 67 to Condition.SLEET,
            61 to Condition.RAIN, 65 to Condition.RAIN, 80 to Condition.RAIN, 82 to Condition.RAIN,
            71 to Condition.SNOW, 77 to Condition.SNOW, 85 to Condition.SNOW, 86 to Condition.SNOW,
            95 to Condition.THUNDERSTORM, 96 to Condition.THUNDERSTORM, 99 to Condition.THUNDERSTORM,
        )
        for ((code, expected) in day) assertEquals(expected, Conditions.from(code, isDay = true, windKmh = 0.0), "code $code")
    }

    @Test
    fun `uses night icons after dark`() {
        assertEquals(Condition.CLEAR_NIGHT, Conditions.from(0, isDay = false, windKmh = 0.0))
        assertEquals(Condition.PARTLY_CLOUDY_NIGHT, Conditions.from(2, isDay = false, windKmh = 0.0))
        assertEquals(Condition.RAIN, Conditions.from(63, isDay = false, windKmh = 0.0))
    }

    @Test
    fun `strong wind overrides dry skies only`() {
        assertEquals(Condition.WINDY, Conditions.from(0, isDay = true, windKmh = 40.0))
        assertEquals(Condition.WINDY, Conditions.from(3, isDay = false, windKmh = 55.0))
        assertEquals(Condition.CLOUDY, Conditions.from(3, isDay = true, windKmh = 39.9))
        assertEquals(Condition.RAIN, Conditions.from(63, isDay = true, windKmh = 70.0))
        assertEquals(Condition.FOG, Conditions.from(45, isDay = true, windKmh = 70.0))
    }
}

class TemperatureUnitTest {
    @Test
    fun `converts and rounds for display`() {
        assertEquals(14, TemperatureUnit.CELSIUS.display(13.7))
        assertEquals(57, TemperatureUnit.FAHRENHEIT.display(13.7))
        assertEquals(-40, TemperatureUnit.FAHRENHEIT.display(-40.0))
        assertEquals(32, TemperatureUnit.FAHRENHEIT.display(0.0))
        assertEquals(-3, TemperatureUnit.CELSIUS.display(-3.2))
    }
}
