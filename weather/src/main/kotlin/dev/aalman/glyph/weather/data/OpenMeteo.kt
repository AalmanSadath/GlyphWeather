package dev.aalman.glyph.weather.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * Client for Open-Meteo's free forecast and geocoding APIs (no key needed).
 *
 * Blocking; call it off the main thread. [get] is swappable so tests can feed fixture JSON.
 */
class OpenMeteo(private val get: (url: String) -> String = ::httpGet) {

    /** Current conditions, today's range and the next [HOURS] hours of rain chance, in °C. */
    fun forecast(latitude: Double, longitude: Double, nowMillis: Long = System.currentTimeMillis()): WeatherSnapshot {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,weather_code,is_day,wind_speed_10m" +
            "&hourly=precipitation_probability" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=2"
        return parseForecast(get(url), nowMillis)
    }

    fun searchCities(query: String): List<City> {
        if (query.isBlank()) return emptyList()
        val url = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=${URLEncoder.encode(query.trim(), "UTF-8")}&count=10&language=en&format=json"
        return parseCities(get(url))
    }

    companion object {
        const val HOURS = 12

        private val json = Json { ignoreUnknownKeys = true }

        internal fun parseForecast(body: String, nowMillis: Long): WeatherSnapshot {
            val r = json.decodeFromString<ForecastResponse>(body)
            // Hourly data starts at local midnight; find the hour we're in. ISO local times in
            // the same zone sort as strings.
            val thisHour = r.current.time.take(13) + ":00"
            val start = r.hourly.time.indexOfFirst { it >= thisHour }.coerceAtLeast(0)
            return WeatherSnapshot(
                fetchedAtMillis = nowMillis,
                temperature = r.current.temperature,
                weatherCode = r.current.weatherCode,
                isDay = r.current.isDay == 1,
                windKmh = r.current.windSpeed,
                todayHigh = r.daily.max.first(),
                todayLow = r.daily.min.first(),
                hourlyPrecipitation = r.hourly.precipitation.drop(start).take(HOURS).map { it ?: 0 },
            )
        }

        internal fun parseCities(body: String): List<City> =
            json.decodeFromString<GeocodingResponse>(body).results

        private fun httpGet(url: String): String {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            try {
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) throw IOException("Open-Meteo returned HTTP $code for $url")
                return conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }

    @Serializable
    private class ForecastResponse(val current: Current, val hourly: Hourly, val daily: Daily)

    @Serializable
    private class Current(
        val time: String,
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("weather_code") val weatherCode: Int,
        @SerialName("is_day") val isDay: Int,
        @SerialName("wind_speed_10m") val windSpeed: Double,
    )

    @Serializable
    private class Hourly(
        val time: List<String>,
        @SerialName("precipitation_probability") val precipitation: List<Int?>,
    )

    @Serializable
    private class Daily(
        @SerialName("temperature_2m_max") val max: List<Double>,
        @SerialName("temperature_2m_min") val min: List<Double>,
    )

    @Serializable
    private class GeocodingResponse(val results: List<City> = emptyList())
}
