package dev.aalman.glyph.weather.data

import dev.aalman.glyph.weather.icons.Condition

/** Maps each source's weather codes onto the conditions there are icons for. */
object Conditions {
    /** Sustained wind at or above this shows the windy icon, unless something is falling. */
    const val WINDY_KMH = 40.0

    /** [sky], or [Condition.WINDY] when nothing is falling ([dry]) and the wind is strong. */
    fun withWind(sky: Condition, dry: Boolean, windKmh: Double): Condition =
        if (dry && windKmh >= WINDY_KMH) Condition.WINDY else sky

    /** Open-Meteo's WMO weather codes. */
    fun fromWmo(code: Int, isDay: Boolean, windKmh: Double): Condition {
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
        return withWind(sky, dry = code in 0..3, windKmh)
    }

    /** Tomorrow.io's `weatherCode` values (docs.tomorrow.io, "Weather Codes"). */
    fun fromTomorrow(code: Int, isDay: Boolean, windKmh: Double): Condition {
        val sky = when (code) {
            1000, 1100 -> if (isDay) Condition.CLEAR_DAY else Condition.CLEAR_NIGHT
            1101, 1102 -> if (isDay) Condition.PARTLY_CLOUDY_DAY else Condition.PARTLY_CLOUDY_NIGHT
            1001 -> Condition.CLOUDY
            2000, 2100 -> Condition.FOG
            4000 -> Condition.DRIZZLE
            4200, 4001, 4201 -> Condition.RAIN
            5000, 5001, 5100, 5101 -> Condition.SNOW
            6000, 6001, 6200, 6201, 7000, 7101, 7102 -> Condition.SLEET
            8000 -> Condition.THUNDERSTORM
            else -> Condition.CLOUDY
        }
        return withWind(sky, dry = code in TOMORROW_DRY, windKmh)
    }

    private val TOMORROW_DRY = setOf(1000, 1100, 1101, 1102, 1001)
}
