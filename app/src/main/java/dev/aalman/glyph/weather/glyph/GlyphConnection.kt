package dev.aalman.glyph.weather.glyph

import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphMatrixManager
import dev.aalman.glyph.weather.icons.Matrix

/**
 * Owns the connection to the Glyph Matrix service.
 *
 * In [Mode.TOY] frames go through `setMatrixFrame`, as a Glyph Toy selected with the Glyph
 * Button. In [Mode.APP] they go through `setAppMatrixFrame`, which an ordinary app uses and any
 * active toy overrides.
 */
class GlyphConnection(context: Context, private val mode: Mode) {
    enum class Mode { TOY, APP }

    private val manager = GlyphMatrixManager.getInstance(context.applicationContext)
    private var ready = false
    private var pending: IntArray? = null

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            manager.register(Glyph.DEVICE_23112)
            ready = true
            pending?.let { show(it) }
            pending = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            ready = false
        }
    }

    fun connect() = manager.init(callback)

    /**
     * Displays one 25×25 frame of brightness values (0–255). A frame sent before the service
     * connects is held and shown once it does.
     */
    fun show(frame: IntArray) {
        require(frame.size == Matrix.CELLS) { "frame must have ${Matrix.CELLS} cells" }
        if (!ready) {
            pending = frame
            return
        }
        val levels = IntArray(Matrix.CELLS) { i -> frame[i].coerceIn(0, 255) * HARDWARE_MAX / 255 }
        try {
            when (mode) {
                Mode.TOY -> manager.setMatrixFrame(levels)
                Mode.APP -> manager.setAppMatrixFrame(levels)
            }
        } catch (e: GlyphException) {
            Log.w(TAG, "Glyph Matrix rejected frame", e)
        }
    }

    fun disconnect() {
        try {
            when (mode) {
                Mode.TOY -> manager.turnOff()
                Mode.APP -> if (ready) manager.closeAppMatrix()
            }
        } catch (e: GlyphException) {
            Log.w(TAG, "Failed to clear Glyph Matrix", e)
        }
        manager.unInit()
        ready = false
        pending = null
    }

    private companion object {
        const val TAG = "GlyphConnection"

        /** Full brightness for the Phone (3) LED driver; 255 is only about 1/16 of it. */
        const val HARDWARE_MAX = 4095
    }
}
