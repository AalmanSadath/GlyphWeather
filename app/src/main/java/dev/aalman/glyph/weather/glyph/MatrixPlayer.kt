package dev.aalman.glyph.weather.glyph

import android.os.Handler
import android.os.Looper
import dev.aalman.glyph.weather.icons.Animation

/**
 * Plays an [Animation] on the matrix: looped, or once and then held on its last frame when
 * [Animation.loop] is false. Shows the still frame when animation is off.
 */
class MatrixPlayer(private val show: (IntArray) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var animation: Animation? = null
    private var index = 0

    private val tick = object : Runnable {
        override fun run() {
            val anim = animation ?: return
            show(anim.frames[index])
            index++
            if (index == anim.frames.size) {
                if (!anim.loop) return // stay on the last frame
                index = 0
            }
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
