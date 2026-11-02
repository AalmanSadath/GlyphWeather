package dev.aalman.glyph.weather.ui

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.aalman.glyph.weather.data.Refresh
import dev.aalman.glyph.weather.data.usableFor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(TRANSPARENT),
        )
        Refresh.schedule(this)
        setContent {
            MaterialTheme(colorScheme = amoledColors()) {
                App()
            }
        }
    }
}

/** The three pages, left to right. */
private enum class Screen { LOCATIONS, WEATHER, SETTINGS }

/**
 * Weather in the middle; the pin opens Locations from the left and the gear opens Settings from
 * the right. Back (button, gesture or arrow) always returns to Weather.
 */
@Composable
private fun App() {
    val context = LocalContext.current
    val app = remember { AppState(context.applicationContext) }
    // Reading these makes the pages redraw whenever the worker, the toy or a page writes.
    app.settings.prefs.changeCount()
    app.cache.prefs.changeCount()
    app.forecasts.prefs.changeCount()

    var screen by rememberSaveable { mutableStateOf(Screen.WEATHER) }
    BackHandler(enabled = screen != Screen.WEATHER) { screen = Screen.WEATHER }
    // Fetch on open if the selected city's forecast is missing or old; the worker skips it otherwise.
    LaunchedEffect(Unit) { Refresh.now(context) }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val enter = slideInHorizontally { width -> if (forward) width else -width }
            val exit = slideOutHorizontally { width -> if (forward) -width / 3 else width / 3 } + fadeOut()
            (enter togetherWith exit).apply { targetContentZIndex = if (targetState == Screen.WEATHER) 0f else 1f }
        },
        label = "pages",
    ) { page ->
        val settings = app.settings
        val city = settings.city
        val source = settings.source
        when (page) {
            Screen.WEATHER -> WeatherScreen(
                state = WeatherPageState(
                    city = city,
                    snapshot = app.cache.snapshot.usableFor(source, city),
                    forecast = city?.let { app.forecasts[it] }.usableFor(source, city),
                    error = app.cache.lastError?.takeIf { it.source == source },
                    source = source,
                    unit = settings.unit,
                    style = settings.iconStyle,
                    animate = settings.animate,
                ),
                onOpenLocations = { screen = Screen.LOCATIONS },
                onOpenSettings = { screen = Screen.SETTINGS },
                onRefresh = app::refresh,
            )
            Screen.LOCATIONS -> {
                val now = System.currentTimeMillis()
                val snapshot = app.cache.snapshot.usableFor(source, city)
                LocationsScreen(
                    searchCountry = settings.effectiveSearchCountry,
                    cards = settings.savedCities.map { saved ->
                        val selected = saved.key == city?.key
                        val hour = app.forecasts[saved].usableFor(source, saved)?.hourAt(now)
                        LocationCard(
                            city = saved,
                            temperature = (if (selected) snapshot?.temperature else null) ?: hour?.temperature,
                            condition = (if (selected) snapshot?.condition else null) ?: hour?.condition,
                            selected = selected,
                        )
                    },
                    unit = settings.unit,
                    style = settings.iconStyle,
                    onBack = { screen = Screen.WEATHER },
                    onSelect = { picked ->
                        app.select(picked)
                        screen = Screen.WEATHER
                    },
                    onRemove = app::remove,
                    onRestore = app::restore,
                    onReorder = app::reorder,
                )
            }
            Screen.SETTINGS -> SettingsScreen(app, onBack = { screen = Screen.WEATHER })
        }
    }
}

/**
 * Always-dark AMOLED theme: wallpaper accent colours on pure black. Dialogs keep a hint of grey
 * so they stand out from the screen behind them.
 */
@Composable
private fun amoledColors() = dynamicDarkColorScheme(LocalContext.current).copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = DIALOG,
    surfaceContainerHighest = Color.Black,
)

private val DIALOG = Color(0xFF121212)
