package dev.aalman.glyph.weather.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json

/**
 * The hourly forecast for each saved location, by [City.key]. The Locations page reads each
 * city's current hour from here, so listing places costs no requests.
 */
class ForecastStore(context: Context) {
    val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    operator fun get(city: City): Forecast? =
        prefs.getString(city.key, null)?.let { runCatching { json.decodeFromString<Forecast>(it) }.getOrNull() }

    fun put(forecast: Forecast) = prefs.edit().putString(forecast.cityKey, json.encodeToString(Forecast.serializer(), forecast)).apply()

    fun remove(city: City) = prefs.edit().remove(city.key).apply()

    /** Forgets every forecast, e.g. when the weather source changes. */
    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val FILE = "forecasts"
        val json = Json { ignoreUnknownKeys = true }
    }
}
