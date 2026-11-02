package dev.aalman.glyph.weather.data

import android.content.Context
import android.content.SharedPreferences
import dev.aalman.glyph.weather.data.source.SourceId
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** User choices, shared between the settings screen, the toy and the refresh worker. */
class Settings(context: Context) {
    val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val secrets: SharedPreferences = context.applicationContext.getSharedPreferences(SECRETS_FILE, Context.MODE_PRIVATE)

    var city: City?
        get() = prefs.getString(KEY_CITY, null)?.let { runCatching { Json.decodeFromString<City>(it) }.getOrNull() }
        set(value) = prefs.edit().putString(KEY_CITY, value?.let { Json.encodeToString(City.serializer(), it) }).apply()

    /**
     * Places the user has searched for, in the order shown on the Locations page. The selected
     * [city] is always one of them; cities saved before the Locations page existed are added.
     */
    var savedCities: List<City>
        get() {
            val saved = prefs.getString(KEY_SAVED, null)
                ?.let { runCatching { Json.decodeFromString<List<City>>(it) }.getOrNull() }
                .orEmpty()
            val selected = city
            return if (selected == null || saved.any { it.key == selected.key }) saved else listOf(selected) + saved
        }
        set(value) = prefs.edit().putString(KEY_SAVED, Json.encodeToString(ListSerializer(City.serializer()), value)).apply()

    /**
     * Country searched first on the Locations page (ISO 3166-1 alpha-2), or null for the
     * phone's own country.
     */
    var searchCountry: String?
        get() = prefs.getString(KEY_SEARCH_COUNTRY, null)
        set(value) = prefs.edit().putString(KEY_SEARCH_COUNTRY, value).apply()

    /** [searchCountry], or the phone's country when that is "Automatic". */
    val effectiveSearchCountry: String?
        get() = searchCountry ?: java.util.Locale.getDefault().country.takeIf { it.length == 2 }

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

    /** Where weather data comes from. Only this source's data is ever shown. */
    var source: SourceId
        get() = prefs.getString(KEY_SOURCE, null)
            ?.let { name -> SourceId.entries.firstOrNull { it.name == name } }
            ?.takeIf { it != SourceId.TOMORROW_IO || tomorrowKey != null }
            ?: SourceId.OPEN_METEO
        set(value) = prefs.edit().putString(KEY_SOURCE, value.name).apply()

    /**
     * The user's Tomorrow.io API key. Kept in its own preferences file, which
     * res/xml/data_extraction_rules.xml leaves out of backups and device transfers.
     */
    var tomorrowKey: String?
        get() = secrets.getString(KEY_TOMORROW, null)?.takeIf { it.isNotBlank() }
        set(value) = secrets.edit().putString(KEY_TOMORROW, value?.trim()).apply()

    /** Minutes between forecast downloads: one of [REFRESH_CHOICES], 30 by default. */
    var refreshMinutes: Int
        get() = prefs.getInt(KEY_REFRESH, 30).takeIf { it in REFRESH_CHOICES } ?: 30
        set(value) = prefs.edit().putInt(KEY_REFRESH, value).apply()

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
        private const val SECRETS_FILE = "secrets"
        private const val KEY_SOURCE = "source"
        private const val KEY_SAVED = "saved_cities"
        private const val KEY_SEARCH_COUNTRY = "search_country"
        private const val KEY_REFRESH = "refresh_minutes"
        val REFRESH_CHOICES = listOf(15, 30, 45, 60)
        private const val KEY_TOMORROW = "tomorrow_key"
        const val KEY_CITY = "city"
        private const val KEY_UNIT = "unit"
        private const val KEY_PAGE_ORDER = "page_order"
        private const val KEY_DISABLED_PAGES = "disabled_pages"
        private const val KEY_ICON_STYLE = "icon_style"
        private const val KEY_ANIMATE = "animate"
        private const val KEY_ANIMATE_AOD = "animate_aod"
    }
}
