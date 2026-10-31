package dev.aalman.glyph.weather.icons

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Original animated weather icons for the Glyph Matrix.
 *
 * Each icon is drawn procedurally from a loop phase t in [0, 1); every moving part completes
 * a whole number of cycles per loop so the animation repeats without a visible seam.
 */
object WeatherIcons {
    private const val FRAMES = 40
    private const val FRAME_MILLIS = 100

    /** Size in LEDs of the icons returned by [small]. */
    const val SMALL_SIZE = SmallIcons.SIZE

    /** Full-screen animation for [condition]. */
    fun animation(condition: Condition): Animation {
        val draw = designs.getValue(condition)
        val frames = List(FRAMES) { f -> Canvas().apply { draw(f.toDouble() / FRAMES) }.snapshot() }
        val still = ((stillPhases[condition] ?: 0.0) * FRAMES).toInt()
        return Animation(frames, FRAME_MILLIS, still)
    }

    /** Loop phase of the frame to show when not animating, where frame 0 isn't representative. */
    private val stillPhases = mapOf(Condition.THUNDERSTORM to 0.125) // mid-flash

    /**
     * Pixel-art [SMALL_SIZE]×[SMALL_SIZE] version of the icon with its top-left corner at
     * ([x], [y]), for layouts that share the matrix with text.
     */
    fun small(condition: Condition, x: Int, y: Int): Animation = SmallIcons.animation(condition, x, y)

    private val designs: Map<Condition, Canvas.(t: Double) -> Unit> = mapOf(
        Condition.CLEAR_DAY to { t -> sun(12.5, 12.5, core = 4.6, rayFrom = 7.0, rayTo = 9.6, t = t) },
        Condition.CLEAR_NIGHT to { t ->
            moon(12.0, 13.0, r = 6.8)
            stars(t, listOf(4.0 to 8.0, 20.0 to 5.0, 21.0 to 17.0, 6.0 to 19.0, 17.0 to 21.0))
        },
        Condition.PARTLY_CLOUDY_DAY to { t ->
            sun(15.0, 9.5, core = 3.6, rayFrom = 5.6, rayTo = 7.4, t = t)
            cloud(10.0 + 0.8 * wave(t), 17.0, 0.9, 235)
        },
        Condition.PARTLY_CLOUDY_NIGHT to { t ->
            moon(14.5, 9.0, r = 5.8)
            stars(t, listOf(5.0 to 7.0, 21.0 to 13.0))
            cloud(10.0 + 0.8 * wave(t), 17.5, 0.9, 235)
        },
        Condition.CLOUDY to { t ->
            cloud(15.5 - 0.8 * wave(t), 8.5, 0.8, 130)
            cloud(10.0 + 0.8 * wave(t), 16.5, 0.9, 235)
        },
        Condition.FOG to { t -> fog(t) },
        Condition.DRIZZLE to { t ->
            precipitation(t, drops = 4, speed = 1, streak = 0.0, flakes = false)
            cloud(12.5, 9.0, 1.0, 235)
        },
        Condition.RAIN to { t ->
            precipitation(t, drops = 7, speed = 2, streak = 1.8, flakes = false)
            cloud(12.5, 9.0, 1.0, 235)
        },
        Condition.SLEET to { t ->
            precipitation(t, drops = 6, speed = 1, streak = 1.4, flakes = true, mixed = true)
            cloud(12.5, 9.0, 1.0, 235)
        },
        Condition.SNOW to { t ->
            precipitation(t, drops = 6, speed = 1, streak = 0.0, flakes = true)
            cloud(12.5, 9.0, 1.0, 235)
        },
        Condition.THUNDERSTORM to { t -> thunderstorm(t) },
        Condition.WINDY to { t -> wind(t) },
    )

    // ---- building blocks ----------------------------------------------------------------

    private fun wave(t: Double, cycles: Int = 1, phase: Double = 0.0) = sin(2 * PI * (cycles * t + phase))

    /** Sun disc with eight rays that turn one ray-step per loop. */
    private fun Canvas.sun(cx: Double, cy: Double, core: Double, rayFrom: Double, rayTo: Double, t: Double) {
        fill(255, circle(cx, cy, core))
        for (k in 0 until 8) {
            val a = 2 * PI * (k + t) / 8
            val pulse = 0.5 + 0.5 * wave(t, cycles = 2, phase = k / 8.0)
            val end = rayTo - 0.6 + 0.6 * pulse
            fill(
                170 + (60 * pulse).toInt(),
                capsule(cx + rayFrom * cos(a), cy + rayFrom * sin(a), cx + end * cos(a), cy + end * sin(a), 0.75),
            )
        }
    }

    /** Crescent: a disc with an offset bite taken out of its upper right. */
    private fun Canvas.moon(cx: Double, cy: Double, r: Double) {
        fill(255, circle(cx, cy, r) minus circle(cx + r * 0.5, cy - r * 0.38, r * 0.82))
    }

    /** Single-LED stars twinkling out of phase with each other. */
    private fun Canvas.stars(t: Double, positions: List<Pair<Double, Double>>) {
        positions.forEachIndexed { i, (x, y) ->
            val glow = 0.5 + 0.5 * wave(t, cycles = 1 + i % 2, phase = i * 0.37)
            dot(x, y, 50 + (205 * glow).toInt())
        }
    }

    /**
     * Cumulus outline centred on (cx, cy): a flat rounded base with two bumps on top.
     * Spans roughly 16×11 LEDs at [s] = 1.
     */
    private fun cloudShape(cx: Double, cy: Double, s: Double, grow: Double = 0.0): Shape =
        capsule(cx - 5.5 * s, cy + 2.2 * s, cx + 5.5 * s, cy + 2.2 * s, 2.3 * s + grow) or
            circle(cx - 3.6 * s, cy - 0.2 * s, 2.9 * s + grow) or
            circle(cx + 1.6 * s, cy - 1.6 * s, 4.2 * s + grow)

    /** Draws a cloud over whatever is behind it, leaving a one-LED dark gap around its edge. */
    private fun Canvas.cloud(cx: Double, cy: Double, s: Double, brightness: Int) {
        erase(cloudShape(cx, cy, s, grow = 1.2))
        fill(brightness, cloudShape(cx, cy, s))
    }

    /** Drifting bands of mist, alternate rows sliding in opposite directions. */
    private fun Canvas.fog(t: Double) {
        val period = 12.0
        val dash = 8.0
        listOf(6.0, 9.5, 13.0, 16.5, 20.0).forEachIndexed { row, y ->
            val dir = if (row % 2 == 0) 1 else -1
            val shift = (dir * t * period + row * 5.0).mod(period)
            var x = -period + shift
            while (x < 25) {
                fill(if (row % 2 == 0) 230 else 140, capsule(x, y, x + dash, y, 0.9))
                x += period
            }
        }
    }

    /**
     * Rain, snow or a mix falling from under a cloud at y ≈ 9.
     * [speed] is how many times each drop crosses the fall zone per loop.
     */
    private fun Canvas.precipitation(
        t: Double,
        drops: Int,
        speed: Int,
        streak: Double,
        flakes: Boolean,
        mixed: Boolean = false,
    ) {
        val top = 12.0
        val bottom = 24.0
        for (i in 0 until drops) {
            val x = 7.5 + i * 10.0 / (drops - 1).coerceAtLeast(1)
            val phase = (i * 0.61).mod(1.0)
            val y = top + ((speed * t + phase).mod(1.0)) * (bottom - top)
            val isFlake = flakes && (!mixed || i % 2 == 1)
            when {
                isFlake -> {
                    val sway = 0.9 * wave(t, cycles = speed, phase = i * 0.3)
                    dot(x + sway, y, 230)
                    if (!mixed && i % 2 == 0) {
                        dot(x + sway - 1, y, 120); dot(x + sway + 1, y, 120)
                        dot(x + sway, y - 1, 120); dot(x + sway, y + 1, 120)
                    }
                }
                streak == 0.0 -> { dot(x, y, 230); dot(x, y - 1, 110) }
                else -> fill(230, capsule(x, y, x + 0.25 * streak, y - streak, 0.6))
            }
        }
    }

    private fun Canvas.thunderstorm(t: Double) {
        precipitation(t, drops = 4, speed = 2, streak = 1.6, flakes = false)
        // A double flash early in the loop and a single strike, mirrored, later on.
        val early = t in 0.10..0.17 || t in 0.25..0.30
        val late = t in 0.62..0.69
        if (early) fill(255, bolt(mirror = false))
        if (late) fill(255, bolt(mirror = true))
        cloud(12.5, 8.5, 1.0, if (early || late) 255 else 160)
    }

    private fun bolt(mirror: Boolean): Shape {
        val points = listOf(
            13.5 to 11.0, 9.5 to 17.5, 12.5 to 17.5, 10.5 to 23.5,
            16.5 to 15.0, 13.5 to 15.0, 15.5 to 11.0,
        )
        return polygon(*points.map { (x, y) -> (if (mirror) 25 - x else x) to y }.toTypedArray())
    }

    /** Gusts: streaks that sweep left to right and curl over at the leading end. */
    private fun Canvas.wind(t: Double) {
        data class Gust(val y: Double, val length: Double, val phase: Double, val curl: Double)
        listOf(
            Gust(7.5, 11.0, 0.0, 2.2),
            Gust(12.5, 14.0, 0.35, 2.6),
            Gust(17.5, 9.0, 0.7, 2.0),
        ).forEach { g ->
            val travel = 25 + g.length + 2 * g.curl
            val head = -2 * g.curl + ((t + g.phase).mod(1.0)) * travel
            val tail = head - g.length
            // Tail fades in from the left so the streak looks like it is gathering speed.
            val steps = 4
            for (s in 0 until steps) {
                val x1 = tail + g.length * s / steps
                val x2 = tail + g.length * (s + 1) / steps
                fill(110 + 145 * (s + 1) / steps, capsule(x1, g.y, x2, g.y, 0.7))
            }
            // Outer half-turn up the right side, then a smaller half-turn back down the left,
            // the two meeting at the top so the curl reads as one spiral stroke.
            val inner = g.curl * 0.6
            fill(255, arc(head, g.y - g.curl, g.curl, 0.7, startDeg = -90.0, sweepDeg = 180.0))
            fill(255, arc(head, g.y - 2 * g.curl + inner, inner, 0.7, startDeg = 90.0, sweepDeg = 180.0))
        }
    }
}
