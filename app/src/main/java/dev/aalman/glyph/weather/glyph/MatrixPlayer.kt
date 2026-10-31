package dev.aalman.glyph.weather.glyph

import android.os.Handler
import android.os.Looper
import dev.aalman.glyph.weather.icons.Animation

/** Loops an [Animation] onto the matrix, or holds its still frame when animation is off. */
class MatrixPlayer(private val show: (IntArray) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var animation: Animation? = null
    private var index = 0

    private val tick = object : Runnable {
        override fun run() {
            val anim = animation ?: return
            show(anim.frames[index])
            index = (index + 1) % anim.frames.size
            handler.postDelayed(this, anim.frameMillis.toLong())
        }
    }

    fun play(animation: Animation, animate: Boolean) {
        stop()
        if (animate && animation.frames.size > 1) {
            this.animation = animation
            index = 0
            tick.run()
        } else {
            show(animation.still)
        }
    }

    fun stop() {
        handler.removeCallbacks(tick)
        animation = null
    }
}
