package dev.aalman.glyph.weather.data

import dev.aalman.glyph.weather.data.source.SourceException
import dev.aalman.glyph.weather.data.source.SourceId
import dev.aalman.glyph.weather.icons.Condition
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/** A place picked from Open-Meteo's geocoding search. */
@Serializable
data class City(
    val name: String,
    val admin1: String? = null,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double,
    /** IANA time zone such as "Europe/Berlin". Missing for cities saved before version 0.3. */
    val timezone: String? = null,
    /** ISO 3166-1 alpha-2 code such as "AU". Missing for cities saved before version 0.3. */
    val countryCode: String? = null,
) {
    /** "Berlin, State of Berlin, Germany", skipping parts that are missing or repeat the name. */
    val label: String
        get() = listOfNotNull(name, admin1?.takeIf { it != name }, country).joinToString(", ")

    /** Identifies the place a snapshot belongs to; 4 decimals is about 10 m. */
    val key: String
        get() = String.format(Locale.ROOT, "%.4f,%.4f", latitude, longitude)

    /** The city's time zone, or the phone's when the city has none. */
    val zone: ZoneId
        get() = timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
}

/**
 * Everything the toy shows, from one source and one city. Temperatures are in °C.
 *
 * [source] and [cityKey] record where it came from: a snapshot whose source or city differs
 * from the current settings is never shown (see [usableFor]).
 */
@Serializable
data class WeatherSnapshot(
    val source: SourceId,
    val cityKey: String,
    val fetchedAtMillis: Long,
    val temperature: Double,
    /** Already resolved for day or night and for strong wind. */
    val condition: Condition,
    val todayHigh: Double,
    val todayLow: Double,
    /** Chance of precipitation (0–100) for the current hour and the hours after it. */
    val hourlyPrecipitation: List<Int>,
    /** Local date ("2026-11-01") that [todayTemps] belong to, for sources that build up today's range. */
    val todayDate: String? = null,
    /** Hourly temperatures seen so far today, by local hour (0–23). */
    val todayTemps: Map<Int, Double> = emptyMap(),
)

/** A failed download, kept so the settings screen can say what went wrong. */
@Serializable
data class SourceError(val source: SourceId, val kind: SourceException.Kind, val atMillis: Long)

/** This snapshot if it came from [source] for [city], otherwise null, so data is never mixed. */
fun WeatherSnapshot?.usableFor(source: SourceId, city: City?): WeatherSnapshot? =
    this?.takeIf { city != null && it.source == source && it.cityKey == city.key }

enum class TemperatureUnit(val symbol: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F");

    /** [celsius] converted to this unit and rounded to a whole degree for display. */
    fun display(celsius: Double): Int = when (this) {
        CELSIUS -> celsius
        FAHRENHEIT -> celsius * 9 / 5 + 32
    }.roundToInt()
}

/** Which weather icons the toy draws. */
enum class IconStyle {
    /** Nothing Weather's widget animations (the `nothing-art` module). */
    NOTHING,

    /** The pixel-art icons in the `icons` module. */
    ORIGINAL,
}
