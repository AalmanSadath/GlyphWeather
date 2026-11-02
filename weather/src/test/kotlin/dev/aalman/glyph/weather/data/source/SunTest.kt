package dev.aalman.glyph.weather.data.source

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class SunTest {
    /** Published sunrise and sunset times in UTC; the sun must be down 10 minutes before and up 10 after. */
    private fun check(lat: Double, lon: Double, sunrise: String, sunset: String) {
        val rise = Instant.parse(sunrise).toEpochMilli()
        val set = Instant.parse(sunset).toEpochMilli()
        val ten = 10 * 60_000L
        assertFalse(Sun.isUp(lat, lon, rise - ten), "up before sunrise $sunrise")
        assertTrue(Sun.isUp(lat, lon, rise + ten), "down after sunrise $sunrise")
        assertTrue(Sun.isUp(lat, lon, set - ten), "down before sunset $sunset")
        assertFalse(Sun.isUp(lat, lon, set + ten), "up after sunset $sunset")
    }

    @Test
    fun `london at both solstices`() {
        check(51.5074, -0.1278, "2026-06-21T03:43:00Z", "2026-06-21T20:21:00Z")
        check(51.5074, -0.1278, "2026-12-21T08:04:00Z", "2026-12-21T15:54:00Z")
    }

    @Test
    fun `sydney at both solstices`() {
        // 07:00 and 16:54 AEST on 21 June; 05:42 and 20:05 AEDT on 21 December.
        check(-33.8688, 151.2093, "2026-06-20T21:00:00Z", "2026-06-21T06:54:00Z")
        check(-33.8688, 151.2093, "2026-12-20T18:42:00Z", "2026-12-21T09:05:00Z")
    }
}
