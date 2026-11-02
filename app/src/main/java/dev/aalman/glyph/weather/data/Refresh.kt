package dev.aalman.glyph.weather.data

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.aalman.glyph.weather.data.source.SourceException
import dev.aalman.glyph.weather.data.source.SourceId
import dev.aalman.glyph.weather.data.source.TomorrowIo
import dev.aalman.glyph.weather.data.source.WeatherSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Schedules forecast downloads in the background. */
object Refresh {
    private const val PERIODIC = "weather-periodic"
    private const val NOW = "weather-now"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * Keeps the background job waking every 15 minutes. UPDATE replaces the 30-minute schedule
     * older versions created without resetting a schedule that already matches.
     */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(
            WeatherCache.WAKE_INTERVAL_MILLIS, TimeUnit.MILLISECONDS,
        ).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * How old the forecast may be before a refresh fetches again. Half a wake interval of slack
     * keeps fetches on the user's interval: with 60 minutes and no slack, a run that wakes when
     * the data is 59 minutes old would skip it, and the next fetch would come at 74.
     */
    fun maxAgeMillis(settings: Settings): Long =
        settings.refreshMinutes * 60_000L - WeatherCache.WAKE_INTERVAL_MILLIS / 2

    /**
     * Fetches as soon as there is a network. With [force] it fetches even when the cached data
     * is still fresh, and restarts a refresh already queued (the city, source or key changed, or
     * the user tapped Refresh now). Without it a queued refresh is left alone.
     */
    fun now(context: Context, force: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(network)
            .setInputData(workDataOf(RefreshWorker.FORCE to force))
            .build()
        val policy = if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, policy, request)
    }
}

/** The one weather source the settings select. Nothing else creates a source for forecasts. */
object Sources {
    fun forSettings(settings: Settings): WeatherSource = when (settings.source) {
        SourceId.OPEN_METEO -> OpenMeteo()
        SourceId.TOMORROW_IO -> settings.tomorrowKey?.let { TomorrowIo(it) } ?: OpenMeteo()
    }
}

/**
 * Runs every 15 minutes. Fetches the selected city at the user's interval, then refreshes the
 * stored forecast of other saved cities once a day. Every request goes to the one selected
 * source.
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = Settings(applicationContext)
        val cache = WeatherCache(applicationContext)
        val store = ForecastStore(applicationContext)
        val source = Sources.forSettings(settings)
        val force = inputData.getBoolean(FORCE, false)

        val selected = settings.city
        val result = if (selected == null) {
            Result.success()
        } else if (!force && !cache.isStale(source.id, selected, Refresh.maxAgeMillis(settings))) {
            Log.d(TAG, "${source.id} data is fresh; skipping")
            Result.success()
        } else {
            fetchSelected(settings, cache, store, source, selected)
        }
        if (result == Result.success()) refreshOthers(settings, store, source)
        return result
    }

    private suspend fun fetchSelected(
        settings: Settings,
        cache: WeatherCache,
        store: ForecastStore,
        source: WeatherSource,
        city: City,
    ): Result = try {
        val previous = cache.snapshot.usableFor(source.id, city)
        val result = withContext(Dispatchers.IO) { source.fetch(city, previous = previous) }
        // The user may have picked another city or source while this was downloading.
        if (settings.city == city && settings.source == source.id) {
            cache.snapshot = result.snapshot
            cache.lastError = null
            store.put(result.forecast)
        }
        Log.d(TAG, "${source.id} refreshed")
        Result.success()
    } catch (e: SourceException) {
        Log.w(TAG, "${source.id} download failed: ${e.kind}", e)
        if (settings.source == source.id) cache.lastError = SourceError(source.id, e.kind, System.currentTimeMillis())
        // A rejected key stays rejected until the user changes it.
        if (e.kind == SourceException.Kind.BAD_KEY) Result.failure() else Result.retry()
    }

    /**
     * Fetches other saved cities whose forecast is missing, from another source, or older than
     * [DAILY_MAX_AGE_MILLIS]. At most [MAX_OTHERS_PER_RUN] per run, 2 seconds apart, so a long
     * list can't use up a source's hourly limit in one go.
     */
    private suspend fun refreshOthers(settings: Settings, store: ForecastStore, source: WeatherSource) {
        val now = System.currentTimeMillis()
        val due = settings.savedCities
            .filter { it.key != settings.city?.key }
            .filter { city ->
                val stored = store[city].usableFor(source.id, city)
                stored == null || now - stored.fetchedAtMillis >= DAILY_MAX_AGE_MILLIS
            }
            .take(MAX_OTHERS_PER_RUN)
        for ((i, city) in due.withIndex()) {
            if (i > 0) delay(2_000)
            try {
                val forecast = withContext(Dispatchers.IO) { source.fetch(city).forecast }
                if (settings.source == source.id && settings.savedCities.any { it.key == city.key }) store.put(forecast)
                Log.d(TAG, "${source.id} daily forecast for ${city.name}")
            } catch (e: SourceException) {
                Log.w(TAG, "${source.id} daily forecast for ${city.name} failed: ${e.kind}")
                return // try the rest on a later run
            }
        }
    }

    companion object {
        const val FORCE = "force"
        private const val TAG = "RefreshWorker"
        const val DAILY_MAX_AGE_MILLIS = 20 * 60 * 60 * 1000L
        private const val MAX_OTHERS_PER_RUN = 5
    }
}
