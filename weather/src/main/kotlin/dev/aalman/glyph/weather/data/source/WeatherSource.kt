package dev.aalman.glyph.weather.data.source

import dev.aalman.glyph.weather.data.City
import dev.aalman.glyph.weather.data.FetchResult
import dev.aalman.glyph.weather.data.WeatherSnapshot
import java.io.IOException

/** The weather services the app can use. Exactly one is active at a time. */
enum class SourceId { OPEN_METEO, TOMORROW_IO }

/**
 * One weather service. Every field of the snapshot it returns comes from this service alone;
 * the app never combines data from two sources.
 */
interface WeatherSource {
    val id: SourceId

    /**
     * Fetches the weather for [city] with one request. Blocking; call it off the main thread.
     *
     * [previous] is the snapshot currently cached. A source may carry values forward from it
     * (Tomorrow.io keeps today's earlier hourly temperatures) but only when it came from the
     * same source and city.
     */
    fun fetch(city: City, nowMillis: Long = System.currentTimeMillis(), previous: WeatherSnapshot? = null): FetchResult
}

/** A failed fetch, typed so the app can show what went wrong and decide whether to retry. */
class SourceException(val kind: Kind, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Kind {
        /** The service rejected the API key (HTTP 401 or 403). Retrying won't help. */
        BAD_KEY,

        /** Too many requests (HTTP 429). */
        RATE_LIMITED,

        /** No connection, a timeout, or another HTTP error. */
        NETWORK,

        /** The service answered with something that couldn't be read. */
        BAD_RESPONSE,
    }
}
