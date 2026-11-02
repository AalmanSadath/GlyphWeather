package dev.aalman.glyph.weather.data

import dev.aalman.glyph.weather.data.source.SourceId
import dev.aalman.glyph.weather.icons.Condition
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One hour of a forecast. [condition] is already resolved for day or night and for wind. */
@Serializable
data class Hour(
    val epochMillis: Long,
    val temperature: Double,
    val condition: Condition,
    /** Chance of precipitation, 0–100. */
    val precipitation: Int,
)

/** A day's summary, worked out from its hours in the city's time zone. */
data class Day(
    val date: LocalDate,
    val low: Double,
    val high: Double,
    val maxPrecipitation: Int,
    /** The condition nearest midday, as the day's icon. */
    val condition: Condition,
)

/**
 * The hourly forecast for one city from one source: up to 7 days from Open-Meteo, 120 hours
 * from Tomorrow.io. Saved for every location so the Locations page can show each one without
 * a request, and used for the Weather page's hourly and daily cards.
 */
@Serializable
data class Forecast(
    val source: SourceId,
    val cityKey: String,
    val fetchedAtMillis: Long,
    val hours: List<Hour>,
) {
    /** The hour containing [millis], or null when the forecast doesn't cover it. */
    fun hourAt(millis: Long): Hour? =
        hours.lastOrNull { it.epochMillis <= millis }?.takeIf { millis - it.epochMillis < HOUR_MILLIS }

    /** Hours from the one containing [millis] onwards. */
    fun hoursFrom(millis: Long): List<Hour> {
        val start = hours.indexOfLast { it.epochMillis <= millis }.coerceAtLeast(0)
        return hours.drop(start)
    }

    /** Whole local days, starting with the day containing [fromMillis]. */
    fun days(zone: ZoneId, fromMillis: Long): List<Day> {
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(fromMillis), zone)
        return hours
            .groupBy { LocalDate.ofInstant(Instant.ofEpochMilli(it.epochMillis), zone) }
            .filterKeys { it >= today }
            .toSortedMap()
            .map { (date, dayHours) ->
                val midday = dayHours.minBy {
                    val hour = Instant.ofEpochMilli(it.epochMillis).atZone(zone).hour
                    kotlin.math.abs(hour - 12)
                }
                Day(
                    date = date,
                    low = dayHours.minOf { it.temperature },
                    high = dayHours.maxOf { it.temperature },
                    maxPrecipitation = dayHours.maxOf { it.precipitation },
                    condition = midday.condition,
                )
            }
    }

    companion object {
        const val HOUR_MILLIS = 3_600_000L
    }
}

/** This forecast if it came from [source] for [city], otherwise null. */
fun Forecast?.usableFor(source: SourceId, city: City?): Forecast? =
    this?.takeIf { city != null && it.source == source && it.cityKey == city.key }

/** What one request returns: today's snapshot and the hourly forecast, from the same response. */
data class FetchResult(val snapshot: WeatherSnapshot, val forecast: Forecast)
