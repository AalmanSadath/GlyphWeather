package dev.aalman.glyph.weather.data.source

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Whether the sun is up, from the standard low-precision solar position formulas (accurate to
 * about a minute of sunrise or sunset time between 1950 and 2050).
 *
 * Used for sources whose current conditions don't say whether it is day or night. It is
 * astronomy, not weather data, so it doesn't mix one service's data into another's.
 */
object Sun {
    /** Sunrise and sunset are when the sun's centre is 0.833° below the horizon (refraction plus its radius). */
    private const val HORIZON = -0.833

    fun isUp(latitude: Double, longitude: Double, epochMillis: Long): Boolean =
        elevation(latitude, longitude, epochMillis) > HORIZON

    /** The sun's elevation above the horizon in degrees. */
    fun elevation(latitude: Double, longitude: Double, epochMillis: Long): Double {
        val n = epochMillis / 86_400_000.0 + 2440587.5 - 2451545.0 // days since J2000.0
        val meanLongitude = (280.460 + 0.9856474 * n).mod(360.0)
        val meanAnomaly = Math.toRadians((357.528 + 0.9856003 * n).mod(360.0))
        val eclipticLongitude = Math.toRadians(meanLongitude + 1.915 * sin(meanAnomaly) + 0.020 * sin(2 * meanAnomaly))
        val obliquity = Math.toRadians(23.439 - 0.0000004 * n)

        val rightAscension = atan2(cos(obliquity) * sin(eclipticLongitude), cos(eclipticLongitude))
        val declination = asin(sin(obliquity) * sin(eclipticLongitude))

        val siderealDegrees = (280.46061837 + 360.98564736629 * n).mod(360.0) + longitude
        val hourAngle = Math.toRadians(siderealDegrees) - rightAscension

        val lat = Math.toRadians(latitude)
        return Math.toDegrees(asin(sin(lat) * sin(declination) + cos(lat) * cos(declination) * cos(hourAngle)))
    }
}
