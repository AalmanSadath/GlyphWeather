package dev.aalman.glyph.weather.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json

/** User choices, shared between the settings screen, the toy and the refresh worker. */
class Settings(context: Context) {
    val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var city: City?
        get() = prefs.getString(KEY_CITY, null)?.let { runCatching { Json.decodeFromString<City>(it) }.getOrNull() }
        set(value) = prefs.edit().putString(KEY_CITY, value?.let { Json.encodeToString(City.serializer(), it) }).apply()

    var unit: TemperatureUnit
        get() = prefs.getString(KEY_UNIT, null)
            ?.let { name -> TemperatureUnit.entries.firstOrNull { it.name == name } }
            ?: TemperatureUnit.CELSIUS
        set(value) = prefs.edit().putString(KEY_UNIT, value.name).apply()

    /** Every page in the user's order, enabled or not. Pages added in later versions go last. */
    var pageOrder: List<Page>
        get() {
            val saved = prefs.getString(KEY_PAGE_ORDER, null)?.split(',')
                ?.mapNotNull { name -> Page.entries.firstOrNull { it.name == name } }
                .orEmpty()
            return (saved + Page.DEFAULT_ORDER).distinct()
        }
        set(value) = prefs.edit().putString(KEY_PAGE_ORDER, value.joinToString(",") { it.name }).apply()

    var disabledPages: Set<Page>
        get() = prefs.getStringSet(KEY_DISABLED_PAGES, emptySet())!!
            .mapNotNull { name -> Page.entries.firstOrNull { it.name == name } }
            .toSet()
        set(value) = prefs.edit().putStringSet(KEY_DISABLED_PAGES, value.map { it.name }.toSet()).apply()

    /** The pages the toy cycles through, in order. Never empty. */
    val enabledPages: List<Page>
        get() = pageOrder.filterNot { it in disabledPages }.ifEmpty { listOf(Page.CURRENT) }

    /** Nothing's animations by default; falls back to the original icons when they aren't bundled. */
    var iconStyle: IconStyle
        get() = prefs.getString(KEY_ICON_STYLE, null)
            ?.let { name -> IconStyle.entries.firstOrNull { it.name == name } }
            ?: IconStyle.NOTHING
        set(value) = prefs.edit().putString(KEY_ICON_STYLE, value.name).apply()

    /** Animate while the phone is in use. */
    var animate: Boolean
        get() = prefs.getBoolean(KEY_ANIMATE, true)
        set(value) = prefs.edit().putBoolean(KEY_ANIMATE, value).apply()

    /** Animate while the Glyph Matrix is the always-on display. */
    var animateAod: Boolean
        get() = prefs.getBoolean(KEY_ANIMATE_AOD, true)
        set(value) = prefs.edit().putBoolean(KEY_ANIMATE_AOD, value).apply()

    companion object {
        private const val FILE = "settings"
        const val KEY_CITY = "city"
        private const val KEY_UNIT = "unit"
        private const val KEY_PAGE_ORDER = "page_order"
        private const val KEY_DISABLED_PAGES = "disabled_pages"
        private const val KEY_ICON_STYLE = "icon_style"
        private const val KEY_ANIMATE = "animate"
        private const val KEY_ANIMATE_AOD = "animate_aod"
    }
}
