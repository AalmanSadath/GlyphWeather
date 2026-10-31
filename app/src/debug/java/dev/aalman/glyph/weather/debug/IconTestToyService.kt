package dev.aalman.glyph.weather.debug

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.nothing.ketchum.GlyphToy
import dev.aalman.glyph.weather.glyph.GlyphConnection
import dev.aalman.glyph.weather.glyph.MatrixPlayer
import dev.aalman.glyph.weather.icons.Condition

/**
 * Debug-only Glyph Toy that steps through every icon: all full-size icons, then all small ones,
 * then the temperature page for each condition.
 * Long-press the Glyph Button to advance.
 */
class IconTestToyService : Service() {
    private var glyph: GlyphConnection? = null
    private var player: MatrixPlayer? = null
    private var index = 0

    private val entries = IconCatalog.View.entries.flatMap { view -> Condition.entries.map { view to it } }

    private val events = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            if (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA) == GlyphToy.EVENT_CHANGE) {
                index = (index + 1) % entries.size
                showCurrent()
            }
        }
    })

    override fun onBind(intent: Intent?): IBinder {
        val connection = GlyphConnection(this, GlyphConnection.Mode.TOY).also { glyph = it }
        player = MatrixPlayer(connection::show)
        connection.connect()
        showCurrent()
        return events.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        player?.stop()
        glyph?.disconnect()
        player = null
        glyph = null
        return false
    }

    private fun showCurrent() {
        val (view, condition) = entries[index]
        player?.play(IconCatalog.get(condition, view, temperature = 23), animate = true)
    }
}
