package dev.aalman.glyph.weather.icons

/**
 * Hand-drawn 12×12 weather icons for layouts that share the matrix with text.
 *
 * Shrinking the full-size icons loses too much detail at this size, so these are pixel art.
 * Sprite characters: `.` off, `:` faint, `+` dim, `#` full. `a` and `b` alternate between
 * full and dim on successive frames (rays pulsing, stars twinkling). Moving parts (rain, snow,
 * fog, wind) are added in code on top of a static sprite.
 */
internal object SmallIcons {
    const val SIZE = 12

    fun animation(condition: Condition, x: Int, y: Int): Animation {
        val (frames, millis) = when (condition) {
            Condition.CLEAR_DAY -> alternating(SUN) to 600
            Condition.CLEAR_NIGHT -> alternating(MOON) to 700
            Condition.PARTLY_CLOUDY_DAY -> alternating(PARTLY_CLOUDY_DAY) to 600
            Condition.PARTLY_CLOUDY_NIGHT -> listOf(sprite(PARTLY_CLOUDY_NIGHT)) to 1000
            Condition.CLOUDY -> listOf(sprite(CLOUDY)) to 1000
            Condition.FOG -> fog() to 200
            Condition.DRIZZLE -> falling(drops = listOf(Fall(4, 0), Fall(8, 3)), streak = false) to 220
            Condition.RAIN -> falling(drops = listOf(Fall(3, 0), Fall(6, 4), Fall(9, 2)), streak = true) to 120
            Condition.SLEET -> falling(
                drops = listOf(Fall(3, 0), Fall(6, 4, flake = true), Fall(9, 2)),
                streak = true,
            ) to 180
            Condition.SNOW -> falling(
                drops = listOf(Fall(3, 0, flake = true), Fall(6, 4, flake = true), Fall(9, 2, flake = true)),
                streak = false,
            ) to 250
            Condition.THUNDERSTORM -> thunderstorm() to 120
            Condition.WINDY -> wind() to 125
        }
        // Frame 2 is a lightning flash; everything else reads fine from its first frame.
        val still = if (condition == Condition.THUNDERSTORM) 2 else 0
        return Animation(frames.map { place(it, x, y) }, millis, still)
    }

    // ---- sprites --------------------------------------------------------------------------

    private val SUN = listOf(
        ".....aa.....",
        ".b...aa...b.",
        "..b......b..",
        "....####....",
        "...######...",
        "aa.######.aa",
        "aa.######.aa",
        "...######...",
        "....####....",
        "..b......b..",
        ".b...aa...b.",
        ".....aa.....",
    )

    private val MOON = listOf(
        "............",
        "....###.....",
        "..####......",
        ".####....a..",
        ".###........",
        "####........",
        "####......b.",
        ".###........",
        ".####...b...",
        "..####......",
        "....###.....",
        "............",
    )

    private val PARTLY_CLOUDY_DAY = listOf(
        ".......aa...",
        ".....b....b.",
        ".......##...",
        "....a.####.a",
        "....a.####.a",
        ".......##...",
        "..........b.",
        ".....###....",
        ".##.#####...",
        "##########..",
        "##########..",
        ".########...",
    )

    private val PARTLY_CLOUDY_NIGHT = listOf(
        "......###...",
        ".....###....",
        "....###.....",
        "....###.....",
        ".....###....",
        "......###...",
        "............",
        ".....###....",
        ".##.#####...",
        "##########..",
        "##########..",
        ".########...",
    )

    private val CLOUDY = listOf(
        "............",
        "........++..",
        "......+++++.",
        ".....+++++++",
        "......+++++.",
        "............",
        ".....###....",
        ".##.#####...",
        "##########..",
        "##########..",
        ".########...",
        "............",
    )

    /** Cloud across the top five rows, leaving rows 6–11 for whatever falls out of it. */
    private val CLOUD_TOP = listOf(
        "......###...",
        "..##.#####..",
        ".##########.",
        ".##########.",
        "..########..",
    )

    private val BOLT = listOf(
        "......##....",
        ".....##.....",
        "....#####...",
        "......##....",
        ".....##.....",
        "....#.......",
    )

    private val WIND = listOf(
        ".......##...",
        "......#..#..",
        ".........#..",
        "#########...",
        "............",
        "............",
        "............", // middle gust, drawn per frame
        "............",
        "............",
        "#######.....",
        "....#..#....",
        ".....##.....",
    )

    // ---- builders -------------------------------------------------------------------------

    private const val DIM = 90
    private const val FALL_TOP = 6
    private const val FALL_ROWS = 6

    /** Two frames with the `a` and `b` pixels swapping between full and dim. */
    private fun alternating(rows: List<String>) = listOf(sprite(rows, phase = 0), sprite(rows, phase = 1))

    private class Fall(val col: Int, val phase: Int, val flake: Boolean = false)

    /** Cloud with drops or flakes falling one row per frame through the bottom six rows. */
    private fun falling(drops: List<Fall>, streak: Boolean) = List(FALL_ROWS) { f ->
        val px = sprite(CLOUD_TOP)
        drawFall(px, f, drops, streak)
        px
    }

    private fun drawFall(px: IntArray, f: Int, drops: List<Fall>, streak: Boolean) {
        for (d in drops) {
            val row = FALL_TOP + (f + d.phase) % FALL_ROWS
            if (d.flake) {
                // Flakes are small crosses (so they read differently from drizzle) and drift one
                // column sideways every other row. Arms above the fall zone would touch the cloud.
                val col = d.col + (row / 2) % 2
                set(px, col, row, 255)
                set(px, col - 1, row, 100)
                set(px, col + 1, row, 100)
                if (row - 1 >= FALL_TOP) set(px, col, row - 1, 100)
                set(px, col, row + 1, 100)
            } else {
                set(px, d.col, row, 255)
                if (streak && row > FALL_TOP) set(px, d.col + 1, row - 1, 120)
            }
        }
    }

    /** Dim cloud with rain, lighting up with a bolt twice per loop. */
    private fun thunderstorm(): List<IntArray> = List(12) { f ->
        val flash = f == 2 || f == 4
        val px = sprite(CLOUD_TOP, brightness = if (flash) 255 else 140)
        drawFall(px, f, listOf(Fall(2, 0), Fall(10, 3)), streak = true)
        if (flash) stamp(px, BOLT, top = FALL_TOP - 1)
        px
    }

    /** Four mist bands, alternate bands drifting in opposite directions. */
    private fun fog(): List<IntArray> = List(6) { f ->
        val px = IntArray(SIZE * SIZE)
        listOf(1, 4, 7, 10).forEachIndexed { i, row ->
            val shift = if (i % 2 == 0) f else -f
            for (col in 0 until SIZE) {
                if ((col - shift + i * 3).mod(6) < 4) set(px, col, row, if (i % 2 == 0) 255 else 140)
            }
        }
        px
    }

    /** Two curling gusts with a dashed gust streaming through the middle. */
    private fun wind(): List<IntArray> = List(8) { f ->
        val px = sprite(WIND)
        for (col in 0 until SIZE) if ((col - f).mod(8) < 6) set(px, col, 6, 200)
        px
    }

    // ---- pixel helpers --------------------------------------------------------------------

    private fun sprite(rows: List<String>, phase: Int = 0, brightness: Int = 255): IntArray {
        val px = IntArray(SIZE * SIZE)
        stamp(px, rows, top = 0, phase = phase, brightness = brightness)
        return px
    }

    private fun stamp(px: IntArray, rows: List<String>, top: Int, phase: Int = 0, brightness: Int = 255) {
        rows.forEachIndexed { r, line ->
            require(line.length == SIZE) { "sprite row \"$line\" must be $SIZE characters" }
            line.forEachIndexed { c, ch ->
                val b = when (ch) {
                    '.' -> 0
                    ':' -> 60
                    '+' -> 140
                    '#' -> brightness
                    'a' -> if (phase == 0) 255 else DIM
                    'b' -> if (phase == 0) DIM else 255
                    else -> error("unknown sprite character '$ch'")
                }
                if (b > 0) set(px, c, top + r, b)
            }
        }
    }

    private fun set(px: IntArray, col: Int, row: Int, b: Int) {
        if (col in 0 until SIZE && row in 0 until SIZE) px[row * SIZE + col] = maxOf(px[row * SIZE + col], b)
    }

    /** Copies a 12×12 sprite onto a blank 25×25 frame at ([x], [y]), dropping cells with no LED. */
    private fun place(sprite: IntArray, x: Int, y: Int): IntArray {
        val frame = IntArray(Matrix.CELLS)
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            val mx = x + c
            val my = y + r
            if (Matrix.isLed(mx, my)) frame[my * Matrix.SIZE + mx] = sprite[r * SIZE + c]
        }
        return frame
    }
}
