package dev.aalman.glyph.weather.data

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** A place picked from Open-Meteo's geocoding search. */
@Serializable
data class City(
    val name: String,
    val admin1: String? = null,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double,
) {
    /** "Berlin, State of Berlin, Germany", skipping parts that are missing or repeat the name. */
    val label: String
        get() = listOfNotNull(name, admin1?.takeIf { it != name }, country).joinToString(", ")
}

/** Everything the toy shows, from one forecast request. Temperatures are in °C. */
@Serializable
data class WeatherSnapshot(
    val fetchedAtMillis: Long,
    val temperature: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val windKmh: Double,
    val todayHigh: Double,
    val todayLow: Double,
    /** Chance of precipitation (0–100) for the current hour and the hours after it. */
    val hourlyPrecipitation: List<Int>,
)

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
