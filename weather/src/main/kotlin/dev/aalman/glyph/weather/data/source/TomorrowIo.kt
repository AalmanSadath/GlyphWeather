package dev.aalman.glyph.weather.data.source

import dev.aalman.glyph.weather.data.City
import dev.aalman.glyph.weather.data.Conditions
import dev.aalman.glyph.weather.data.FetchResult
import dev.aalman.glyph.weather.data.Forecast
import dev.aalman.glyph.weather.data.Hour
import dev.aalman.glyph.weather.data.OpenMeteo
import dev.aalman.glyph.weather.data.WeatherSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Tomorrow.io's Timelines API, with the user's own API key.
 *
 * One request per refresh, for 4 fields: the current values plus [HOURS_AHEAD] hourly values,
 * the most the free plan returns. Tomorrow.io counts it as one request whatever the length.
 *
 * Today's high and low come from hourly temperatures, not Tomorrow.io's daily values, so "today"
 * is the city's local day. The forecast only covers hours from now on, so earlier hours are
 * carried forward from the previous snapshot when it is from Tomorrow.io, for the same city and
 * the same local date. The first refresh of a day therefore covers only the rest of that day.
 */
class TomorrowIo(private val apiKey: String, private val http: Http = Http.Default) : WeatherSource {
    override val id = SourceId.TOMORROW_IO

    override fun fetch(city: City, nowMillis: Long, previous: WeatherSnapshot?): FetchResult {
        val zone = city.zone
        val url = "https://api.tomorrow.io/v4/timelines?apikey=${URLEncoder.encode(apiKey, "UTF-8")}"
        val result = http.request(url, requestBody(city, zone, nowMillis))
        checkStatus("Tomorrow.io", result)
        return parse(result.body, city, zone, nowMillis, previous)
    }

    companion object {
        val FIELDS = listOf("temperature", "weatherCode", "precipitationProbability", "windSpeed")

        /** Hourly forecast length: 120 hours (5 days), the free plan's maximum. */
        const val HOURS_AHEAD = 120L

        private val json = Json { ignoreUnknownKeys = true }

        internal fun requestBody(city: City, zone: ZoneId, nowMillis: Long): String = buildJsonObject {
            put("location", "${city.latitude},${city.longitude}")
            putJsonArray("fields") { FIELDS.forEach { add(it) } }
            put("units", "metric")
            putJsonArray("timesteps") { add("current"); add("1h") }
            put("startTime", "now")
            put("endTime", Instant.ofEpochMilli(nowMillis).plus(HOURS_AHEAD, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString())
            put("timezone", zone.id)
        }.toString()

        internal fun parse(body: String, city: City, zone: ZoneId, nowMillis: Long, previous: WeatherSnapshot?): FetchResult {
            val timelines = try {
                json.decodeFromString<Response>(body).data.timelines
            } catch (e: SerializationException) {
                throw SourceException(SourceException.Kind.BAD_RESPONSE, "Unreadable Tomorrow.io response", e)
            } catch (e: IllegalArgumentException) {
                throw SourceException(SourceException.Kind.BAD_RESPONSE, "Unreadable Tomorrow.io response", e)
            }
            val current = timelines.firstOrNull { it.timestep == "current" }?.intervals?.firstOrNull()?.values
            val hourly = timelines.firstOrNull { it.timestep == "1h" }?.intervals.orEmpty()
            val temperature = current?.temperature ?: hourly.firstOrNull()?.values?.temperature
                ?: throw SourceException(SourceException.Kind.BAD_RESPONSE, "Tomorrow.io response has no temperature")
            val code = current?.weatherCode ?: hourly.firstOrNull()?.values?.weatherCode ?: 0
            val windKmh = (current?.windSpeed ?: hourly.firstOrNull()?.values?.windSpeed ?: 0.0) * 3.6

            val today = LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
            val nowHour = Instant.ofEpochMilli(nowMillis).atZone(zone).hour
            val carried = previous
                ?.takeIf { it.source == SourceId.TOMORROW_IO && it.cityKey == city.key && it.todayDate == today.toString() }
                ?.todayTemps.orEmpty()
            val fresh = mutableMapOf<Int, Double>()
            for (interval in hourly) {
                val t = interval.values.temperature ?: continue
                val start = parseTime(interval.startTime)?.atZoneSameInstant(zone) ?: continue
                if (start.toLocalDate() == today) fresh[start.hour] = t
            }
            fresh[nowHour] = temperature
            val todayTemps = (carried + fresh).toSortedMap()

            val hours = hourly.mapNotNull { interval ->
                val v = interval.values
                val t = v.temperature ?: return@mapNotNull null
                val start = parseTime(interval.startTime)?.toInstant()?.toEpochMilli() ?: return@mapNotNull null
                // Day or night at the middle of the hour.
                val isDay = Sun.isUp(city.latitude, city.longitude, start + 30 * 60_000L)
                Hour(start, t, Conditions.fromTomorrow(v.weatherCode ?: 0, isDay, (v.windSpeed ?: 0.0) * 3.6),
                    (v.precipitationProbability ?: 0.0).roundToInt())
            }
            val snapshot = WeatherSnapshot(
                source = SourceId.TOMORROW_IO,
                cityKey = city.key,
                fetchedAtMillis = nowMillis,
                temperature = temperature,
                condition = Conditions.fromTomorrow(code, Sun.isUp(city.latitude, city.longitude, nowMillis), windKmh),
                todayHigh = todayTemps.values.max(),
                todayLow = todayTemps.values.min(),
                hourlyPrecipitation = hourly.take(OpenMeteo.HOURS).map { (it.values.precipitationProbability ?: 0.0).roundToInt() },
                todayDate = today.toString(),
                todayTemps = todayTemps,
            )
            return FetchResult(snapshot, Forecast(SourceId.TOMORROW_IO, city.key, nowMillis, hours))
        }

        private fun parseTime(text: String): OffsetDateTime? =
            try { OffsetDateTime.parse(text) } catch (e: DateTimeParseException) { null }
    }

    @Serializable
    private class Response(val data: Data)

    @Serializable
    private class Data(val timelines: List<Timeline>)

    @Serializable
    private class Timeline(val timestep: String, val intervals: List<Interval>)

    @Serializable
    private class Interval(val startTime: String, val values: Values)

    @Serializable
    private class Values(
        val temperature: Double? = null,
        val weatherCode: Int? = null,
        val precipitationProbability: Double? = null,
        val windSpeed: Double? = null,
    )
}
