package dev.aalman.glyph.weather.data

import android.content.Context
import android.content.SharedPreferences
import dev.aalman.glyph.weather.data.source.SourceId
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

    /** Why the last download failed, or null after a success. */
    var lastError: SourceError?
        get() = prefs.getString(KEY_ERROR, null)
            ?.let { runCatching { Json.decodeFromString<SourceError>(it) }.getOrNull() }
        set(value) = prefs.edit()
            .putString(KEY_ERROR, value?.let { Json.encodeToString(SourceError.serializer(), it) })
            .apply()

    /** Forgets the forecast and any error, e.g. when the city or source changes. */
    fun clear() = prefs.edit().remove(KEY_SNAPSHOT).remove(KEY_ERROR).apply()

    /**
     * True unless there is a snapshot from [source] for [city] younger than [maxAgeMillis].
     * A snapshot from another source or city counts as missing.
     */
    fun isStale(
        source: SourceId,
        city: City?,
        maxAgeMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val fetched = snapshot.usableFor(source, city)?.fetchedAtMillis ?: return true
        return nowMillis - fetched >= maxAgeMillis
    }

    companion object {
        private const val FILE = "weather"
        private const val KEY_SNAPSHOT = "snapshot"
        private const val KEY_ERROR = "error"

        /**
         * How often the background job wakes up: 15 minutes, the shortest period WorkManager
         * allows. Each run fetches only when the forecast is older than the user's interval.
         */
        const val WAKE_INTERVAL_MILLIS = 15 * 60 * 1000L
    }
}
