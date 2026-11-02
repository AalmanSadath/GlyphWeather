package dev.aalman.glyph.weather.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.aalman.glyph.weather.R
import dev.aalman.glyph.weather.data.City
import dev.aalman.glyph.weather.data.FetchResult
import dev.aalman.glyph.weather.data.ForecastStore
import dev.aalman.glyph.weather.data.Refresh
import dev.aalman.glyph.weather.data.Settings
import dev.aalman.glyph.weather.data.WeatherCache
import dev.aalman.glyph.weather.data.source.SourceId
import dev.aalman.glyph.weather.icons.Condition

/** The app's stored state and the changes the pages make to it, in one place. */
class AppState(private val context: Context) {
    val settings = Settings(context)
    val cache = WeatherCache(context)
    val forecasts = ForecastStore(context)

    /** Makes [city] the selected one, adding it to the top of the saved list if it's new. */
    fun select(city: City) {
        if (city.key == settings.city?.key) return
        if (settings.savedCities.none { it.key == city.key }) settings.savedCities = listOf(city) + settings.savedCities
        settings.city = city
        cache.clear() // the snapshot belongs to the old city; the new one's forecast stays
        Refresh.now(context, force = true)
    }

    /** Removes [city]. If it was selected, the next saved city (or none) is selected. */
    fun remove(city: City) {
        val remaining = settings.savedCities.filter { it.key != city.key }
        settings.savedCities = remaining
        forecasts.remove(city)
        if (settings.city?.key == city.key) {
            val next = remaining.firstOrNull()
            settings.city = next
            cache.clear()
            if (next != null) Refresh.now(context, force = true)
        }
    }

    /** Puts a removed [city] back at [index], e.g. after Undo. Its forecast is fetched again. */
    fun restore(city: City, index: Int) {
        val list = settings.savedCities.toMutableList()
        if (list.any { it.key == city.key }) return
        list.add(index.coerceIn(0, list.size), city)
        settings.savedCities = list
        if (settings.city == null) select(city) else Refresh.now(context)
    }

    fun reorder(cities: List<City>) {
        settings.savedCities = cities
    }

    /** Switches to [source]; every stored forecast is from the old one, so all are discarded. */
    fun useSource(source: SourceId) {
        if (source == settings.source) return
        settings.source = source
        cache.clear()
        forecasts.clear()
        Refresh.now(context, force = true)
    }

    /** A Tomorrow.io key passed its check; [result] is the forecast that check downloaded. */
    fun acceptTomorrowKey(key: String, result: FetchResult?) {
        settings.tomorrowKey = key
        settings.source = SourceId.TOMORROW_IO
        cache.clear()
        forecasts.clear()
        if (result != null) {
            cache.snapshot = result.snapshot
            forecasts.put(result.forecast)
        }
        Refresh.now(context, force = result == null)
    }

    fun removeTomorrowKey() {
        settings.tomorrowKey = null
        useSource(SourceId.OPEN_METEO)
    }

    fun refresh() = Refresh.now(context, force = true)
}

/** Increments whenever any value in these preferences changes, so readers recompose. */
@Composable
fun SharedPreferences.changeCount(): Int {
    var count by remember { mutableIntStateOf(0) }
    DisposableEffect(this) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> count++ }
        registerOnSharedPreferenceChangeListener(listener)
        onDispose { unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return count
}

fun conditionName(condition: Condition): Int = when (condition) {
    Condition.CLEAR_DAY, Condition.CLEAR_NIGHT -> R.string.condition_clear
    Condition.PARTLY_CLOUDY_DAY, Condition.PARTLY_CLOUDY_NIGHT -> R.string.condition_partly_cloudy
    Condition.CLOUDY -> R.string.condition_cloudy
    Condition.FOG -> R.string.condition_fog
    Condition.DRIZZLE -> R.string.condition_drizzle
    Condition.RAIN -> R.string.condition_rain
    Condition.SLEET -> R.string.condition_sleet
    Condition.SNOW -> R.string.condition_snow
    Condition.THUNDERSTORM -> R.string.condition_thunderstorm
    Condition.WINDY -> R.string.condition_windy
}

fun sourceName(source: SourceId): Int = when (source) {
    SourceId.OPEN_METEO -> R.string.source_open_meteo
    SourceId.TOMORROW_IO -> R.string.source_tomorrow
}
