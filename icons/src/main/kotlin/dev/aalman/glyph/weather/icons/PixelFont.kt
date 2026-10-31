package dev.aalman.glyph.weather.icons

/**
 * 7-pixel-tall proportional font for temperatures: digits, minus, degree sign, and `?` for
 * missing data.
 * Glyphs are 3–4 columns wide with one blank column between them.
 */
object PixelFont {
    const val HEIGHT = 7
    private const val SPACING = 1

    private val glyphs: Map<Char, List<String>> = mapOf(
        '0' to listOf(".##.", "#..#", "#..#", "#..#", "#..#", "#..#", ".##."),
        '1' to listOf(".#.", "##.", ".#.", ".#.", ".#.", ".#.", "###"),
        '2' to listOf(".##.", "#..#", "...#", "..#.", ".#..", "#...", "####"),
        '3' to listOf(".##.", "#..#", "...#", ".##.", "...#", "#..#", ".##."),
        '4' to listOf("..#.", ".##.", "#.#.", "#.#.", "####", "..#.", "..#."),
        '5' to listOf("####", "#...", "###.", "...#", "...#", "#..#", ".##."),
        '6' to listOf(".##.", "#...", "#...", "###.", "#..#", "#..#", ".##."),
        '7' to listOf("####", "...#", "..#.", "..#.", ".#..", ".#..", ".#.."),
        '8' to listOf(".##.", "#..#", "#..#", ".##.", "#..#", "#..#", ".##."),
        '9' to listOf(".##.", "#..#", "#..#", ".###", "...#", "...#", ".##."),
        '-' to listOf("...", "...", "...", "###", "...", "...", "..."),
        '°' to listOf(".#.", "#.#", ".#.", "...", "...", "...", "..."),
        '?' to listOf(".##.", "#..#", "...#", "..#.", ".#..", "....", ".#.."),
    )

    /** Width in LEDs of [text] when drawn. */
    fun width(text: String): Int =
        text.sumOf { glyph(it).first().length } + SPACING * (text.length - 1).coerceAtLeast(0)

    /** Draws [text] onto a 25×25 [frame] with its top-left corner at ([x], [y]). */
    fun draw(frame: IntArray, text: String, x: Int, y: Int, brightness: Int = 255) {
        var left = x
        for (ch in text) {
            val rows = glyph(ch)
            rows.forEachIndexed { r, line ->
                line.forEachIndexed { c, px ->
                    val mx = left + c
                    val my = y + r
                    if (px == '#' && Matrix.isLed(mx, my)) frame[my * Matrix.SIZE + mx] = brightness
                }
            }
            left += rows.first().length + SPACING
        }
    }

    private fun glyph(ch: Char) = glyphs[ch] ?: error("PixelFont has no glyph for '$ch'")
}
