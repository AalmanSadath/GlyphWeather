package dev.aalman.glyph.weather.data

import dev.aalman.glyph.weather.icons.Condition

/** Maps Open-Meteo's WMO weather codes onto the conditions we have icons for. */
object Conditions {
    /** Sustained wind at or above this shows the windy icon, unless something is falling. */
    const val WINDY_KMH = 40.0

    fun from(code: Int, isDay: Boolean, windKmh: Double): Condition {
        val sky = when (code) {
            0, 1 -> if (isDay) Condition.CLEAR_DAY else Condition.CLEAR_NIGHT
            2 -> if (isDay) Condition.PARTLY_CLOUDY_DAY else Condition.PARTLY_CLOUDY_NIGHT
            3 -> Condition.CLOUDY
            45, 48 -> Condition.FOG
            51, 53, 55 -> Condition.DRIZZLE
            56, 57, 66, 67 -> Condition.SLEET
            61, 63, 65, 80, 81, 82 -> Condition.RAIN
            71, 73, 75, 77, 85, 86 -> Condition.SNOW
            95, 96, 99 -> Condition.THUNDERSTORM
            else -> Condition.CLOUDY
        }
        val dry = code in 0..3
        return if (dry && windKmh >= WINDY_KMH) Condition.WINDY else sky
    }

    fun from(snapshot: WeatherSnapshot): Condition = from(snapshot.weatherCode, snapshot.isDay, snapshot.windKmh)
}
