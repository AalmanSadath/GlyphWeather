package dev.aalman.glyph.weather.data

import dev.aalman.glyph.weather.data.source.Http
import dev.aalman.glyph.weather.data.source.SourceException
import dev.aalman.glyph.weather.data.source.SourceId
import dev.aalman.glyph.weather.data.source.WeatherSource
import dev.aalman.glyph.weather.data.source.checkStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/**
 * Open-Meteo's free forecast and geocoding APIs (no key needed). The default weather source,
 * and the city search for every source.
 *
 * Blocking; call it off the main thread. [http] is swappable so tests can feed fixture JSON.
 */
class OpenMeteo(private val http: Http = Http.Default) : WeatherSource {
    override val id = SourceId.OPEN_METEO

    /** Current conditions, today's range and 7 days of hourly data, in °C. */
    override fun fetch(city: City, nowMillis: Long, previous: WeatherSnapshot?): FetchResult {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${city.latitude}&longitude=${city.longitude}" +
            "&current=temperature_2m,weather_code,is_day,wind_speed_10m" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,is_day,wind_speed_10m" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=$DAYS&timeformat=unixtime"
        return parseForecast(get(url), city, nowMillis)
    }

    /**
     * Places matching [query], best first. Open-Meteo matches on the name alone and returns
     * them worldwide in its own order, so this asks for its maximum of 100 and then:
     * - keeps towns, suburbs and administrative areas, dropping mountains, parks and the like;
     * - narrows by anything after a comma ("Epping, New South Wales" or "Epping, AU");
     * - ranks places in [searchCountry] first, then [preferredCountries] (ISO codes), then
     *   [preferredRegions] (state or province names), then larger places.
     *
     * With [searchCountry] set, a second request asks for up to 100 places in that country
     * alone, so its places can't be crowded out of the worldwide 100.
     */
    fun searchCities(
        query: String,
        searchCountry: String? = null,
        preferredCountries: Set<String> = emptySet(),
        preferredRegions: Set<String> = emptySet(),
    ): List<City> {
        val parts = query.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val name = parts.firstOrNull() ?: return emptyList()
        val base = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=${URLEncoder.encode(name, "UTF-8")}&count=$SEARCH_LIMIT&language=en&format=json"
        val local = searchCountry?.let { decode<GeocodingResponse>(get("$base&countryCode=$it")).results }.orEmpty()
        val world = decode<GeocodingResponse>(get(base)).results
        val places = (local + world).distinctBy { "${it.latitude},${it.longitude}" }
        return rankPlaces(places, parts.drop(1), setOfNotNull(searchCountry), preferredCountries, preferredRegions)
    }

    private fun get(url: String): String {
        val result = http.request(url, null)
        checkStatus("Open-Meteo", result)
        return result.body
    }

    companion object {
        const val HOURS = 12

        /** Days of hourly data saved per location. */
        const val DAYS = 7

        private val json = Json { ignoreUnknownKeys = true }

        internal fun parseForecast(body: String, city: City, nowMillis: Long): FetchResult {
            val r = decode<ForecastResponse>(body)
            val h = r.hourly
            val hours = h.time.indices.mapNotNull { i ->
                val temperature = h.temperature.getOrNull(i) ?: return@mapNotNull null
                Hour(
                    epochMillis = h.time[i] * 1000,
                    temperature = temperature,
                    condition = Conditions.fromWmo(
                        h.weatherCode.getOrNull(i) ?: 3,
                        h.isDay.getOrNull(i) == 1,
                        h.windSpeed.getOrNull(i) ?: 0.0,
                    ),
                    precipitation = h.precipitation.getOrNull(i) ?: 0,
                )
            }
            val forecast = Forecast(SourceId.OPEN_METEO, city.key, nowMillis, hours)
            // Hourly data starts at local midnight; start the rain slice at the hour we're in.
            val snapshot = WeatherSnapshot(
                source = SourceId.OPEN_METEO,
                cityKey = city.key,
                fetchedAtMillis = nowMillis,
                temperature = r.current.temperature,
                condition = Conditions.fromWmo(r.current.weatherCode, r.current.isDay == 1, r.current.windSpeed),
                todayHigh = r.daily.max.first(),
                todayLow = r.daily.min.first(),
                hourlyPrecipitation = forecast.hoursFrom(r.current.time * 1000).take(HOURS).map { it.precipitation },
            )
            return FetchResult(snapshot, forecast)
        }

        internal fun parseCities(body: String): List<City> = decode<GeocodingResponse>(body).results.map { it.toCity() }

        /** Open-Meteo returns at most 100 results per search. */
        private const val SEARCH_LIMIT = 100

        /** How many search results to show. */
        const val SHOWN_RESULTS = 20

        internal fun parseAndRank(
            body: String,
            qualifiers: List<String>,
            preferredCountries: Set<String>,
            preferredRegions: Set<String> = emptySet(),
            searchCountry: String? = null,
        ): List<City> = rankPlaces(
            decode<GeocodingResponse>(body).results, qualifiers, setOfNotNull(searchCountry), preferredCountries, preferredRegions,
        )

        private fun rankPlaces(
            results: List<Place>,
            qualifiers: List<String>,
            firstCountries: Set<String>,
            preferredCountries: Set<String>,
            preferredRegions: Set<String>,
        ): List<City> {
            val wanted = qualifiers.map { it.lowercase() }
            val first = firstCountries.map { it.uppercase() }.toSet()
            val preferred = preferredCountries.map { it.uppercase() }.toSet()
            return results
                .filter { it.featureCode == null || it.featureCode.startsWith("PPL") || it.featureCode.startsWith("ADM") }
                .filter { place ->
                    val fields = listOfNotNull(place.admin1, place.admin2, place.country, place.countryCode).map { it.lowercase() }
                    wanted.all { q -> fields.any { it.startsWith(q) } }
                }
                .sortedWith(
                    compareByDescending<Place> { it.countryCode?.uppercase() in first }
                        .thenByDescending { it.countryCode?.uppercase() in preferred }
                        .thenByDescending { it.admin1 in preferredRegions }
                        .thenByDescending { it.population ?: 0L },
                )
                .take(SHOWN_RESULTS)
                .map { it.toCity() }
        }

        private inline fun <reified T> decode(body: String): T = try {
            json.decodeFromString<T>(body)
        } catch (e: SerializationException) {
            throw SourceException(SourceException.Kind.BAD_RESPONSE, "Unreadable Open-Meteo response", e)
        } catch (e: IllegalArgumentException) {
            throw SourceException(SourceException.Kind.BAD_RESPONSE, "Unreadable Open-Meteo response", e)
        }
    }

    @Serializable
    private class ForecastResponse(val current: Current, val hourly: Hourly, val daily: Daily)

    @Serializable
    private class Current(
        val time: Long,
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("weather_code") val weatherCode: Int,
        @SerialName("is_day") val isDay: Int,
        @SerialName("wind_speed_10m") val windSpeed: Double,
    )

    @Serializable
    private class Hourly(
        val time: List<Long>,
        @SerialName("temperature_2m") val temperature: List<Double?>,
        @SerialName("weather_code") val weatherCode: List<Int?>,
        @SerialName("precipitation_probability") val precipitation: List<Int?>,
        @SerialName("is_day") val isDay: List<Int?>,
        @SerialName("wind_speed_10m") val windSpeed: List<Double?>,
    )

    @Serializable
    private class Daily(
        @SerialName("temperature_2m_max") val max: List<Double>,
        @SerialName("temperature_2m_min") val min: List<Double>,
    )

    @Serializable
    private class GeocodingResponse(val results: List<Place> = emptyList())

    /** One geocoding result, with the fields used for filtering and ranking. */
    @Serializable
    private class Place(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val admin1: String? = null,
        val admin2: String? = null,
        val country: String? = null,
        @SerialName("country_code") val countryCode: String? = null,
        val timezone: String? = null,
        @SerialName("feature_code") val featureCode: String? = null,
        val population: Long? = null,
    ) {
        fun toCity() = City(name, admin1, country, latitude, longitude, timezone, countryCode)
    }
}
