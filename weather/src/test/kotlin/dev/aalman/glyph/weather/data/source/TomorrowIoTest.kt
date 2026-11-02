package dev.aalman.glyph.weather.data.source

import dev.aalman.glyph.weather.data.City
import dev.aalman.glyph.weather.data.Conditions
import dev.aalman.glyph.weather.data.WeatherSnapshot
import dev.aalman.glyph.weather.data.usableFor
import dev.aalman.glyph.weather.icons.Condition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class TomorrowIoTest {
    private val body = javaClass.getResource("/tomorrow_sydney.json")!!.readText()
    private val sydney = City("Sydney", latitude = -33.8688, longitude = 151.2093, timezone = "Australia/Sydney")
    private val zone = ZoneId.of("Australia/Sydney")
    // tomorrow_sydney.json is a real 120-hour response, requested at 13:01 AEDT on 2 November 2026.
    private val now = Instant.parse("2026-11-02T02:01:14Z").toEpochMilli()

    private fun parse(previous: WeatherSnapshot? = null, city: City = sydney, at: Long = now) =
        TomorrowIo.parse(body, city, zone, at, previous).snapshot

    @Test
    fun `maps current values and the next 12 hours of rain`() {
        val s = parse()
        assertEquals(SourceId.TOMORROW_IO, s.source)
        assertEquals(sydney.key, s.cityKey)
        assertEquals(23.21, s.temperature)
        assertEquals(Condition.PARTLY_CLOUDY_DAY, s.condition) // code 1101, early afternoon, 5.8 m/s wind
        assertEquals(listOf(5, 0, 0, 0, 0, 0, 20, 25, 25, 0, 0, 0), s.hourlyPrecipitation)
    }

    @Test
    fun `today's range uses only the city's local date`() {
        val s = parse()
        // 13:00–23:00 forecast plus the current reading; later entries are other days.
        assertEquals(23.21, s.todayHigh)
        assertEquals(19.46, s.todayLow)
        assertEquals("2026-11-02", s.todayDate)
        assertEquals((13..23).toList(), s.todayTemps.keys.toList())
    }

    @Test
    fun `earlier hours carry over from the same source, city and day`() {
        val morning = parse().copy(todayTemps = mapOf(6 to 12.3, 9 to 17.0, 14 to 99.0))
        val s = parse(previous = morning)
        assertEquals(12.3, s.todayLow)
        assertEquals(23.21, s.todayHigh) // the stale 99.0 at 14:00 was replaced
        assertEquals(listOf(6, 9) + (13..23).toList(), s.todayTemps.keys.toList())
    }

    @Test
    fun `other sources, cities and days start afresh`() {
        val cold = parse().copy(todayTemps = mapOf(5 to -3.0))
        assertEquals(19.46, parse(previous = cold.copy(source = SourceId.OPEN_METEO)).todayLow)
        assertEquals(19.46, parse(previous = cold.copy(cityKey = "0.0000,0.0000")).todayLow)
        assertEquals(19.46, parse(previous = cold.copy(todayDate = "2026-10-31")).todayLow)
    }

    @Test
    fun `asks for 4 fields and 120 hours in one request`() {
        val request = Json.parseToJsonElement(TomorrowIo.requestBody(sydney, zone, now)).jsonObject
        assertEquals(TomorrowIo.FIELDS, request["fields"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("Australia/Sydney", request["timezone"]!!.jsonPrimitive.content)
        assertEquals("2026-11-07T02:01:14Z", request["endTime"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the hourly forecast covers 120 hours split into local days`() {
        val forecast = TomorrowIo.parse(body, sydney, zone, now, null).forecast
        assertEquals(SourceId.TOMORROW_IO, forecast.source)
        assertEquals(121, forecast.hours.size)
        val days = forecast.days(zone, now)
        assertEquals(6, days.size)
        assertEquals(java.time.LocalDate.parse("2026-11-03"), days[1].date)
        assertEquals(18.42, days[1].low)
        assertEquals(24.58, days[1].high)
        assertEquals(50, days[1].maxPrecipitation)
    }

    @Test
    fun `HTTP errors become typed source errors`() {
        fun kind(code: Int) = assertThrows(SourceException::class.java) {
            TomorrowIo("key") { _, _ -> HttpResult(code, "{}") }.fetch(sydney, now)
        }.kind
        assertEquals(SourceException.Kind.BAD_KEY, kind(401))
        assertEquals(SourceException.Kind.BAD_KEY, kind(403))
        assertEquals(SourceException.Kind.RATE_LIMITED, kind(429))
        assertEquals(SourceException.Kind.NETWORK, kind(500))
        val garbage = TomorrowIo("key") { _, _ -> HttpResult(200, "{\"data\":{}}") }
        assertEquals(SourceException.Kind.BAD_RESPONSE, assertThrows(SourceException::class.java) { garbage.fetch(sydney, now) }.kind)
    }

    @Test
    fun `the key goes only into the request URL`() {
        var url = ""
        var sent = ""
        TomorrowIo("secret key") { u, b -> url = u; sent = b!!; HttpResult(200, body) }.fetch(sydney, now)
        assertTrue(url.startsWith("https://api.tomorrow.io/v4/timelines?apikey=secret+key"))
        assertTrue("secret" !in sent)
    }

    @Test
    fun `maps Tomorrow io weather codes`() {
        val day = mapOf(
            1000 to Condition.CLEAR_DAY, 1100 to Condition.CLEAR_DAY, 1101 to Condition.PARTLY_CLOUDY_DAY,
            1102 to Condition.PARTLY_CLOUDY_DAY, 1001 to Condition.CLOUDY, 2000 to Condition.FOG, 2100 to Condition.FOG,
            4000 to Condition.DRIZZLE, 4200 to Condition.RAIN, 4001 to Condition.RAIN, 4201 to Condition.RAIN,
            5000 to Condition.SNOW, 5001 to Condition.SNOW, 5100 to Condition.SNOW, 5101 to Condition.SNOW,
            6000 to Condition.SLEET, 6001 to Condition.SLEET, 6200 to Condition.SLEET, 6201 to Condition.SLEET,
            7000 to Condition.SLEET, 7101 to Condition.SLEET, 7102 to Condition.SLEET,
            8000 to Condition.THUNDERSTORM, 0 to Condition.CLOUDY,
        )
        for ((code, expected) in day) assertEquals(expected, Conditions.fromTomorrow(code, isDay = true, windKmh = 0.0), "code $code")
        assertEquals(Condition.CLEAR_NIGHT, Conditions.fromTomorrow(1000, isDay = false, windKmh = 0.0))
        assertEquals(Condition.WINDY, Conditions.fromTomorrow(1001, isDay = true, windKmh = 40.0))
        assertEquals(Condition.RAIN, Conditions.fromTomorrow(4001, isDay = true, windKmh = 80.0))
    }

    @Test
    fun `snapshots are only usable for their own source and city`() {
        val s = parse()
        assertSame(s, s.usableFor(SourceId.TOMORROW_IO, sydney))
        assertNull(s.usableFor(SourceId.OPEN_METEO, sydney))
        assertNull(s.usableFor(SourceId.TOMORROW_IO, sydney.copy(latitude = 0.0)))
        assertNull(s.usableFor(SourceId.TOMORROW_IO, null))
    }
}
