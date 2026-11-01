package dev.aalman.glyph.weather.debug

import android.app.Activity
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.aalman.glyph.weather.art.NothingArt
import dev.aalman.glyph.weather.glyph.GlyphConnection
import dev.aalman.glyph.weather.glyph.MatrixPlayer
import dev.aalman.glyph.weather.icons.Condition
import dev.aalman.glyph.weather.icons.Matrix
import dev.aalman.glyph.weather.icons.Pages

/**
 * Debug-only screen for reviewing every icon on the real Glyph Matrix. Tap a condition to show
 * it on the back of the phone; switch between full icon, small icon and the temperature page;
 * toggle animation; show a brightness ramp.
 *
 * Uses app-matrix mode, so it only shows while no Glyph Toy is active.
 */
class IconTestActivity : Activity() {
    private lateinit var glyph: GlyphConnection
    private lateinit var player: MatrixPlayer
    private lateinit var status: TextView

    private var view = IconCatalog.View.FULL
    private var temperatureIndex = 0
    private var hiLoIndex = 0
    private var rainIndex = 0
    private var animate = true
    private var current: Condition? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        glyph = GlyphConnection(this, GlyphConnection.Mode.APP)
        player = MatrixPlayer(glyph::show)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 48)
        }
        status = TextView(this).apply { textSize = 16f }
        column.addView(status)

        column.addView(button("View: ${view.label}") { b ->
            view = IconCatalog.View.entries[(view.ordinal + 1) % IconCatalog.View.entries.size]
            b.text = "View: ${view.label}"
            replay()
        })
        column.addView(button("Temp: ${temperature()}°") { b ->
            temperatureIndex = (temperatureIndex + 1) % IconCatalog.sampleTemperatures.size
            b.text = "Temp: ${temperature()}°"
            replay()
        })
        column.addView(button("Animate: on") { b ->
            animate = !animate
            b.text = if (animate) "Animate: on" else "Animate: off"
            replay()
        })

        for (c in Condition.entries) {
            column.addView(button(c.name) { current = c; replay() })
        }

        for (hot in listOf(true, false)) {
            val label = if (hot) "Nothing extreme heat" else "Nothing extreme cold"
            column.addView(button(label) {
                val anim = NothingArt.extreme(hot) ?: return@button
                current = null
                player.play(anim, animate)
                status.text = "Showing: $label"
            })
        }

        column.addView(button("Hi/Lo page (tap for next sample)") {
            val (high, low) = IconCatalog.sampleHiLo[hiLoIndex]
            hiLoIndex = (hiLoIndex + 1) % IconCatalog.sampleHiLo.size
            showStatic("Hi/Lo $high° / $low°", Pages.hiLo(high, low).still)
        })
        column.addView(button("Rain page (tap for next sample)") {
            val chances = IconCatalog.sampleRain[rainIndex]
            rainIndex = (rainIndex + 1) % IconCatalog.sampleRain.size
            showStatic("Rain $chances", Pages.rainBar(chances).still)
        })
        column.addView(button("No-data page") { showStatic("No data", Pages.unknown().still) })
        column.addView(button("Brightness ramp") { showStatic("Brightness ramp", IconCatalog.ramp()) })
        column.addView(button("Off") { showStatic("Off", IntArray(Matrix.CELLS)) })

        // Targeting API 35+ draws edge to edge, so keep the buttons clear of the status and nav bars.
        setContentView(ScrollView(this).apply {
            addView(column)
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        })
        updateStatus()
        glyph.connect()
    }

    override fun onDestroy() {
        player.stop()
        glyph.disconnect()
        super.onDestroy()
    }

    private fun replay() {
        val c = current ?: return updateStatus()
        player.play(IconCatalog.get(c, view, temperature()), animate)
        updateStatus()
    }

    private fun showStatic(label: String, frame: IntArray) {
        current = null
        player.stop()
        glyph.show(frame)
        status.text = "Showing: $label"
    }

    private fun updateStatus() {
        val anim = if (animate) "animated" else "static"
        status.text = "Showing: ${current?.name ?: "nothing"} (${view.label}, $anim)"
    }

    private fun temperature() = IconCatalog.sampleTemperatures[temperatureIndex]

    private fun button(label: String, onClick: (Button) -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick(this) }
    }
}
