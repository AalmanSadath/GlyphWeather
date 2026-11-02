package dev.aalman.glyph.weather.data

import dev.aalman.glyph.weather.data.source.HttpResult
import dev.aalman.glyph.weather.data.source.SourceException
import dev.aalman.glyph.weather.data.source.SourceId
import dev.aalman.glyph.weather.icons.Condition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenMeteoTest {
    private fun fixture(name: String) = javaClass.getResource("/$name")!!.readText()
    private val london = City("London", latitude = 51.5, longitude = -0.13, timezone = "Europe/London")

    @Test
    fun `parses a real 7-day forecast response`() {
        // forecast_london.json: a real response, current time 03:00 GMT on 2 November 2026.
        val (s, forecast) = OpenMeteo.parseForecast(fixture("forecast_london.json"), london, nowMillis = 42)

        assertEquals(SourceId.OPEN_METEO, s.source)
        assertEquals(london.key, s.cityKey)
        assertEquals(42, s.fetchedAtMillis)
        assertEquals(10.4, s.temperature)
        assertEquals(Condition.CLOUDY, s.condition) // code 3 at night, 15.8 km/h wind
        assertEquals(11.9, s.todayHigh)
        assertEquals(9.0, s.todayLow)
        // The rain slice starts at the 03:00 entry, the third hour of the day.
        assertEquals(listOf(45, 57, 45, 22, 4, 0, 0, 0, 2, 5, 8, 13), s.hourlyPrecipitation)

        assertEquals(SourceId.OPEN_METEO, forecast.source)
        assertEquals(london.key, forecast.cityKey)
        assertEquals(168, forecast.hours.size)
        assertEquals(7, forecast.days(london.zone, 1793584800_000).size)
    }

    @Test
    fun `hourly slice crosses midnight and treats missing values as zero`() {
        val t0 = 1793491200L // 2026-11-01T00:00Z
        val times = (0 until 48).joinToString(",") { h -> "${t0 + h * 3600}" }
        val chances = (0 until 48).joinToString(",") { h -> if (h == 25) "null" else "$h" }
        val temps = (0 until 48).joinToString(",") { "-3.0" }
        val codes = (0 until 48).joinToString(",") { "71" }
        val zeros = (0 until 48).joinToString(",") { "0" }
        val body = """
            {"current":{"time":${t0 + 23 * 3600 + 45 * 60},"temperature_2m":-3.2,"weather_code":71,"is_day":0,"wind_speed_10m":5.0},
             "hourly":{"time":[$times],"temperature_2m":[$temps],"weather_code":[$codes],"precipitation_probability":[$chances],
                       "is_day":[$zeros],"wind_speed_10m":[$zeros]},
             "daily":{"temperature_2m_max":[1.0,2.0],"temperature_2m_min":[-5.0,-4.0]}}
        """.trimIndent()

        val s = OpenMeteo.parseForecast(body, london, nowMillis = 0).snapshot

        assertEquals(listOf(23, 24, 0, 26, 27, 28, 29, 30, 31, 32, 33, 34), s.hourlyPrecipitation)
        assertEquals(Condition.SNOW, s.condition)
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
    fun `HTTP errors become typed source errors`() {
        val client = OpenMeteo { _, _ -> HttpResult(429, "Too many requests") }
        val e = assertThrows(SourceException::class.java) { client.fetch(london, 0) }
        assertEquals(SourceException.Kind.RATE_LIMITED, e.kind)
        val garbage = OpenMeteo { _, _ -> HttpResult(200, "<html>") }
        assertEquals(SourceException.Kind.BAD_RESPONSE, assertThrows(SourceException::class.java) { garbage.fetch(london, 0) }.kind)
    }

    @Test
    fun `search ranks suburbs in the user's country first and drops parks and mountains`() {
        // geocoding_castle_hill.json: a real 100-result response; Castle Hill, NSW is 41st in it.
        val body = fixture("geocoding_castle_hill.json")
        val ranked = OpenMeteo.parseAndRank(body, emptyList(), setOf("AU"))
        assertEquals("New South Wales", ranked[0].admin1)
        assertEquals("AU", ranked[0].countryCode)
        assertTrue(ranked.none { "Park" in it.name })
        assertTrue(ranked.size <= OpenMeteo.SHOWN_RESULTS)
    }

    @Test
    fun `the search country outranks saved countries`() {
        val body = fixture("geocoding_castle_hill.json")
        val nz = OpenMeteo.parseAndRank(body, emptyList(), setOf("AU"), setOf("New South Wales"), searchCountry = "NZ")
        assertEquals("NZ", nz[0].countryCode)
    }

    @Test
    fun `a search country adds a second request limited to it`() {
        val urls = mutableListOf<String>()
        OpenMeteo { url, _ -> urls += url; HttpResult(200, fixture("geocoding_castle_hill.json")) }
            .searchCities("Castle Hill", searchCountry = "AU")
        assertEquals(2, urls.size)
        assertTrue(urls.any { it.endsWith("&countryCode=AU") })
        assertTrue(urls.any { "countryCode" !in it })
    }

    @Test
    fun `places in the user's saved states come next`() {
        val body = fixture("geocoding_castle_hill.json")
        val qld = OpenMeteo.parseAndRank(body, emptyList(), setOf("AU"), setOf("Queensland"))
        assertEquals("Queensland", qld[0].admin1)
    }

    @Test
    fun `text after a comma narrows by state or country`() {
        val body = fixture("geocoding_castle_hill.json")
        val nsw = OpenMeteo.parseAndRank(body, listOf("New South"), emptySet())
        assertEquals(listOf("New South Wales"), nsw.map { it.admin1 }.distinct())
        val nz = OpenMeteo.parseAndRank(body, listOf("nz"), emptySet())
        assertTrue(nz.isNotEmpty() && nz.all { it.countryCode == "NZ" })
    }

    @Test
    fun `search sends only the name before the comma`() {
        var requested = ""
        OpenMeteo { url, _ -> requested = url; HttpResult(200, fixture("geocoding_castle_hill.json")) }
            .searchCities("Castle Hill, New South Wales")
        assertTrue("name=Castle+Hill&" in requested, requested)
        assertTrue("count=100" in requested)
    }

    @Test
    fun `geocoding keeps the time zone`() {
        assertEquals("Europe/Berlin", OpenMeteo.parseCities(fixture("geocoding_berlin.json")).first().timezone)
    }

    @Test
    fun `blank search makes no request`() {
        val client = OpenMeteo { _, _ -> error("should not be called") }
        assertEquals(emptyList<City>(), client.searchCities("  "))
    }

    @Test
    fun `forecast request asks for 7 days of the fields the parser reads`() {
        var requested = ""
        OpenMeteo { url, _ -> requested = url; HttpResult(200, fixture("forecast_london.json")) }.fetch(london, 0)
        for (part in listOf("temperature_2m", "weather_code", "is_day", "wind_speed_10m", "precipitation_probability",
            "temperature_2m_max", "temperature_2m_min", "forecast_days=7", "timeformat=unixtime")) {
            assertTrue(part in requested, "missing $part")
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
        for ((code, expected) in day) assertEquals(expected, Conditions.fromWmo(code, isDay = true, windKmh = 0.0), "code $code")
    }

    @Test
    fun `uses night icons after dark`() {
        assertEquals(Condition.CLEAR_NIGHT, Conditions.fromWmo(0, isDay = false, windKmh = 0.0))
        assertEquals(Condition.PARTLY_CLOUDY_NIGHT, Conditions.fromWmo(2, isDay = false, windKmh = 0.0))
        assertEquals(Condition.RAIN, Conditions.fromWmo(63, isDay = false, windKmh = 0.0))
    }

    @Test
    fun `strong wind overrides dry skies only`() {
        assertEquals(Condition.WINDY, Conditions.fromWmo(0, isDay = true, windKmh = 40.0))
        assertEquals(Condition.WINDY, Conditions.fromWmo(3, isDay = false, windKmh = 55.0))
        assertEquals(Condition.CLOUDY, Conditions.fromWmo(3, isDay = true, windKmh = 39.9))
        assertEquals(Condition.RAIN, Conditions.fromWmo(63, isDay = true, windKmh = 70.0))
        assertEquals(Condition.FOG, Conditions.fromWmo(45, isDay = true, windKmh = 70.0))
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
