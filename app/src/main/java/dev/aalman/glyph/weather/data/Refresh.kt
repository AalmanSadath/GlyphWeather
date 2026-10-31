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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Schedules forecast downloads in the background. */
object Refresh {
    private const val PERIODIC = "weather-periodic"
    private const val NOW = "weather-now"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Keeps a refresh running every 30 minutes; calling it again leaves the existing schedule. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(
            WeatherCache.REFRESH_INTERVAL_MILLIS, TimeUnit.MILLISECONDS,
        ).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Fetches as soon as there is a network. With [replace] a refresh already queued is
     * restarted (the city changed); otherwise a queued one is left alone.
     */
    fun now(context: Context, replace: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(network).build()
        val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, policy, request)
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = Settings(applicationContext)
        val city = settings.city ?: return Result.success()
        return try {
            val snapshot = withContext(Dispatchers.IO) { OpenMeteo().forecast(city.latitude, city.longitude) }
            // The user may have picked another city while this was downloading.
            if (settings.city == city) WeatherCache(applicationContext).snapshot = snapshot
            Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "Forecast download failed; will retry", e)
            Result.retry()
        } catch (e: SerializationException) {
            Log.w(TAG, "Unexpected forecast response; will retry", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "RefreshWorker"
    }
}
