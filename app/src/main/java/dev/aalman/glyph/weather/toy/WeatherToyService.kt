package dev.aalman.glyph.weather.toy

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.PowerManager
import android.util.Log
import com.nothing.ketchum.GlyphToy
import dev.aalman.glyph.weather.data.IconStyle
import dev.aalman.glyph.weather.data.Page
import dev.aalman.glyph.weather.data.PageRenderer
import dev.aalman.glyph.weather.data.Refresh
import dev.aalman.glyph.weather.data.Settings
import dev.aalman.glyph.weather.data.TemperatureUnit
import dev.aalman.glyph.weather.data.WeatherCache
import dev.aalman.glyph.weather.data.WeatherSnapshot
import dev.aalman.glyph.weather.data.usableFor
import dev.aalman.glyph.weather.glyph.GlyphConnection
import dev.aalman.glyph.weather.glyph.MatrixPlayer

/**
 * The weather Glyph Toy. Shows the user's enabled pages one at a time; a long press on the
 * Glyph Button moves to the next. Animation follows the "animate" setting while the phone is in
 * use and the "animate on always-on display" setting while the screen is off.
 */
class WeatherToyService : Service() {
    private lateinit var settings: Settings
    private lateinit var cache: WeatherCache
    private lateinit var power: PowerManager
    private val renderer = PageRenderer()

    private var glyph: GlyphConnection? = null
    private var player: MatrixPlayer? = null
    private var pageIndex = 0
    private var shown: Shown? = null

    /** What is on the matrix now, so redraw requests that change nothing are skipped. */
    private data class Shown(
        val page: Page,
        val snapshot: WeatherSnapshot?,
        val unit: TemperatureUnit,
        val style: IconStyle,
        val animate: Boolean,
    )

    private val events = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            val event = msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)
            Log.d(TAG, "event $event")
            when (event) {
                GlyphToy.EVENT_CHANGE -> {
                    pageIndex++
                    render()
                }
                GlyphToy.EVENT_AOD -> {
                    refreshIfStale()
                    render()
                }
            }
        }
    })

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "broadcast ${intent.action}")
            // Nothing's animations play once; waking the phone plays the current one again.
            if (intent.action == Intent.ACTION_SCREEN_ON) shown = null
            render()
        }
    }

    private val prefsChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render() }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind")
        settings = Settings(this)
        cache = WeatherCache(this)
        power = getSystemService(PowerManager::class.java)

        val connection = GlyphConnection(this, GlyphConnection.Mode.TOY).also { glyph = it }
        player = MatrixPlayer(connection::show)
        connection.connect()

        settings.prefs.registerOnSharedPreferenceChangeListener(prefsChanged)
        cache.prefs.registerOnSharedPreferenceChangeListener(prefsChanged)
        registerReceiver(
            screen,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            RECEIVER_NOT_EXPORTED,
        )

        Refresh.schedule(this)
        refreshIfStale()
        render()
        return events.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        unregisterReceiver(screen)
        settings.prefs.unregisterOnSharedPreferenceChangeListener(prefsChanged)
        cache.prefs.unregisterOnSharedPreferenceChangeListener(prefsChanged)
        player?.stop()
        glyph?.disconnect()
        player = null
        glyph = null
        shown = null
        return false
    }

    private fun refreshIfStale() {
        if (settings.city != null && cache.isStale(settings.source, settings.city, Refresh.maxAgeMillis(settings))) Refresh.now(this)
    }

    private fun render() {
        val player = player ?: return
        val pages = settings.enabledPages
        val page = pages[pageIndex.mod(pages.size)]
        // Data from another source or city is never shown, even for a moment after a switch.
        val snapshot = cache.snapshot.usableFor(settings.source, settings.city)
        val unit = settings.unit
        val animate = if (power.isInteractive) settings.animate else settings.animateAod

        Log.d(TAG, "render page=$page interactive=${power.isInteractive} animate=$animate")
        val style = settings.iconStyle
        val next = Shown(page, snapshot, unit, style, animate)
        if (next == shown) return
        shown = next
        player.play(renderer.render(page, snapshot, unit, style), animate)
    }

    private companion object {
        const val TAG = "WeatherToy"
    }
}
