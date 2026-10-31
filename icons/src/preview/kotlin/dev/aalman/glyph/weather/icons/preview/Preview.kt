package dev.aalman.glyph.weather.icons.preview

import dev.aalman.glyph.weather.icons.Animation
import dev.aalman.glyph.weather.icons.Condition
import dev.aalman.glyph.weather.icons.Matrix
import dev.aalman.glyph.weather.icons.Pages
import dev.aalman.glyph.weather.icons.WeatherIcons
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.FileImageOutputStream

private const val LED = 14 // pixels per LED cell in the preview
private const val MATRIX_PX = Matrix.SIZE * LED
private const val KEY_FRAMES = 6
private val SAMPLE_TEMPERATURES = listOf(23, -5, 0, -12, 104, 7)
private val SAMPLE_HI_LO = listOf(23 to 12, 104 to 81, -5 to -18, 0 to -12, 9 to 1, 31 to 19)
private val SAMPLE_RAIN = listOf(
    List(12) { 0 },
    listOf(0, 0, 5, 10, 20, 40, 70, 90, 100, 80, 50, 20),
    listOf(100, 90, 60, 30, 10, 0, 0, 0, 0, 0, 0, 0),
    listOf(10, 30, 10, 60, 20, 80, 40, 100, 30, 50, 10, 0),
    List(12) { 100 },
    List(12) { 35 },
)

/**
 * Writes, for every [Condition]:
 *  - `<condition>.gif`: the full-size animation as it would loop on the phone;
 *  - `<condition>_small.gif`: the pixel-art small icon, centred;
 * plus `contact_sheet.png` (evenly spaced key frames of every full-size icon) and
 * `contact_sheet_small.png` (the first frames of every small icon) and
 * `contact_sheet_current.png` (the icon-plus-temperature page at sample temperatures) and
 * `contact_sheet_pages.png` (high/low, rain and no-data pages with sample data).
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/icon-preview").apply { mkdirs() }
    val rows = Condition.entries.map { c ->
        val full = WeatherIcons.animation(c)
        val offset = (Matrix.SIZE - WeatherIcons.SMALL_SIZE) / 2
        val small = WeatherIcons.small(c, x = offset, y = offset)
        writeGif(full, File(out, "${c.name.lowercase()}.gif"))
        writeGif(small, File(out, "${c.name.lowercase()}_small.gif"))
        Triple(c, full, small)
    }
    writeContactSheet(rows.map { (c, full, _) -> c.name to keyFrames(full) }, File(out, "contact_sheet.png"))
    writeContactSheet(rows.map { (c, _, small) -> c.name to small.frames.take(KEY_FRAMES) }, File(out, "contact_sheet_small.png"))
    writeContactSheet(
        Condition.entries.map { c -> c.name to SAMPLE_TEMPERATURES.map { t -> Pages.current(c, t).still } },
        File(out, "contact_sheet_current.png"),
    )
    writeContactSheet(
        listOf(
            "HI / LO" to SAMPLE_HI_LO.map { (hi, lo) -> Pages.hiLo(hi, lo).still },
            "RAIN" to SAMPLE_RAIN.map { Pages.rainBar(it).still },
            "NO DATA" to listOf(Pages.unknown().still),
        ),
        File(out, "contact_sheet_pages.png"),
    )
    println("Icon previews written to ${out.absolutePath}")
}

/** Draws one frame the way the hardware shows it: round LEDs, dim grey when off, nothing outside the circle. */
private fun drawMatrix(g: Graphics2D, frame: IntArray, ox: Int, oy: Int) {
    g.color = Color(12, 12, 12)
    g.fillOval(ox - LED / 2, oy - LED / 2, MATRIX_PX + LED, MATRIX_PX + LED)
    for (y in 0 until Matrix.SIZE) for (x in 0 until Matrix.SIZE) {
        if (!Matrix.isLed(x, y)) continue
        val b = frame[y * Matrix.SIZE + x]
        val v = if (b == 0) 34 else 50 + b * 205 / 255
        g.color = Color(v, v, v)
        val pad = 2
        g.fillOval(ox + x * LED + pad, oy + y * LED + pad, LED - 2 * pad, LED - 2 * pad)
    }
}

private fun frameImage(frame: IntArray): BufferedImage {
    val img = BufferedImage(MATRIX_PX + LED * 2, MATRIX_PX + LED * 2, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color.BLACK
    g.fillRect(0, 0, img.width, img.height)
    drawMatrix(g, frame, LED, LED)
    g.dispose()
    return img
}

private fun writeGif(animation: Animation, file: File) {
    val writer = ImageIO.getImageWritersByFormatName("gif").next()
    file.delete()
    FileImageOutputStream(file).use { stream ->
        writer.output = stream
        writer.prepareWriteSequence(null)
        animation.frames.forEachIndexed { i, frame ->
            val img = frameImage(frame)
            val params = writer.defaultWriteParam
            val meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(img), params)
            val format = meta.nativeMetadataFormatName
            val root = meta.getAsTree(format) as IIOMetadataNode
            root.child("GraphicControlExtension").apply {
                setAttribute("disposalMethod", "none")
                setAttribute("userInputFlag", "FALSE")
                setAttribute("transparentColorFlag", "FALSE")
                setAttribute("delayTime", (animation.frameMillis / 10).toString())
                setAttribute("transparentColorIndex", "0")
            }
            if (i == 0) {
                val ext = root.child("ApplicationExtensions")
                ext.appendChild(IIOMetadataNode("ApplicationExtension").apply {
                    setAttribute("applicationID", "NETSCAPE")
                    setAttribute("authenticationCode", "2.0")
                    userObject = byteArrayOf(1, 0, 0) // loop forever
                })
            }
            meta.setFromTree(format, root)
            writer.writeToSequence(IIOImage(img, null, meta), params)
        }
        writer.endWriteSequence()
    }
    writer.dispose()
}

private fun IIOMetadataNode.child(name: String): IIOMetadataNode {
    for (i in 0 until length) {
        val node = item(i)
        if (node.nodeName == name) return node as IIOMetadataNode
    }
    return IIOMetadataNode(name).also { appendChild(it) }
}

private fun keyFrames(animation: Animation) =
    List(KEY_FRAMES) { k -> animation.frames[k * animation.frames.size / KEY_FRAMES] }

private fun writeContactSheet(rows: List<Pair<String, List<IntArray>>>, file: File) {
    val cell = MATRIX_PX + LED * 2
    val label = 200
    val width = label + cell * KEY_FRAMES
    val height = cell * rows.size
    val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = Color.BLACK
    g.fillRect(0, 0, width, height)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, 20)
    rows.forEachIndexed { r, (name, frames) ->
        val top = r * cell
        g.color = Color.WHITE
        g.drawString(name, 12, top + cell / 2)
        frames.forEachIndexed { k, frame -> drawMatrix(g, frame, label + k * cell + LED, top + LED) }
    }
    g.dispose()
    ImageIO.write(img, "png", file)
}
