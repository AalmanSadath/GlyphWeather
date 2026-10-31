package dev.aalman.glyph.weather.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json

/** The most recent forecast, kept so the toy can draw immediately without the network. */
class WeatherCache(context: Context) {
    val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var snapshot: WeatherSnapshot?
        get() = prefs.getString(KEY_SNAPSHOT, null)
            ?.let { runCatching { Json.decodeFromString<WeatherSnapshot>(it) }.getOrNull() }
        set(value) = prefs.edit()
            .putString(KEY_SNAPSHOT, value?.let { Json.encodeToString(WeatherSnapshot.serializer(), it) })
            .apply()

    /** True when there is no forecast or it is older than [maxAgeMillis]. */
    fun isStale(nowMillis: Long = System.currentTimeMillis(), maxAgeMillis: Long = REFRESH_INTERVAL_MILLIS): Boolean {
        val fetched = snapshot?.fetchedAtMillis ?: return true
        return nowMillis - fetched >= maxAgeMillis
    }

    companion object {
        private const val FILE = "weather"
        private const val KEY_SNAPSHOT = "snapshot"
        const val REFRESH_INTERVAL_MILLIS = 30 * 60 * 1000L
    }
}
