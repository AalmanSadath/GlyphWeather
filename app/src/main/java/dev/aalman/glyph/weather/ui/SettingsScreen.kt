package dev.aalman.glyph.weather.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.aalman.glyph.weather.R
import dev.aalman.glyph.weather.art.NothingArt
import dev.aalman.glyph.weather.data.Conditions
import dev.aalman.glyph.weather.data.IconStyle
import dev.aalman.glyph.weather.data.Page
import dev.aalman.glyph.weather.data.Refresh
import dev.aalman.glyph.weather.data.Settings
import dev.aalman.glyph.weather.data.TemperatureUnit
import dev.aalman.glyph.weather.data.WeatherCache
import dev.aalman.glyph.weather.data.WeatherSnapshot
import dev.aalman.glyph.weather.icons.Condition
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val cache = remember { WeatherCache(context) }
    // Reading these makes the screen redraw whenever the worker, the toy or this screen writes.
    // Child composables take plain values, not these objects: Compose skips a child whose
    // arguments are the same instances as last time, so it would miss the change.
    settings.prefs.changeCount()
    cache.prefs.changeCount()

    var searching by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section(R.string.section_location) {
                Text(settings.city?.label ?: stringResource(R.string.no_city), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { searching = true }) {
                    Text(stringResource(if (settings.city == null) R.string.choose_city else R.string.change_city))
                }
            }

            if (settings.city != null) {
                WeatherStatus(cache.snapshot, settings.unit, onRefresh = { Refresh.now(context, replace = true) })
            }

            Section(R.string.section_units) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TemperatureUnit.entries.forEachIndexed { i, unit ->
                        SegmentedButton(
                            selected = settings.unit == unit,
                            onClick = { settings.unit = unit },
                            shape = SegmentedButtonDefaults.itemShape(i, TemperatureUnit.entries.size),
                        ) { Text(unit.symbol) }
                    }
                }
            }

            if (NothingArt.available) {
                IconStyleChoice(settings.iconStyle) { settings.iconStyle = it }
            }

            Section(R.string.section_pages) {
                Text(stringResource(R.string.pages_hint), style = MaterialTheme.typography.bodySmall)
                PageList(
                    order = settings.pageOrder,
                    disabled = settings.disabledPages,
                    onOrderChange = { settings.pageOrder = it },
                    onDisabledChange = { settings.disabledPages = it },
                )
            }

            Section(R.string.section_animation) {
                SwitchRow(R.string.animate, R.string.animate_summary, settings.animate) { settings.animate = it }
                SwitchRow(R.string.animate_aod, R.string.animate_aod_summary, settings.animateAod) { settings.animateAod = it }
            }

            Button(onClick = { openToysManager(context) }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.activate_toy))
            }
            TextButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/"))) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(8.dp),
            ) { Text(stringResource(R.string.attribution), style = MaterialTheme.typography.bodySmall) }
            if (NothingArt.available) {
                Text(
                    stringResource(R.string.art_credit),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (searching) {
        CitySearchDialog(
            onPick = { city ->
                searching = false
                if (city != settings.city) {
                    settings.city = city
                    cache.snapshot = null // belongs to the old city
                    Refresh.now(context, replace = true)
                }
            },
            onDismiss = { searching = false },
        )
    }
}

@Composable
private fun WeatherStatus(snapshot: WeatherSnapshot?, unit: TemperatureUnit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    Section(R.string.section_weather) {
        if (snapshot == null) {
            Text(stringResource(R.string.weather_waiting))
        } else {
            val temp = "${unit.display(snapshot.temperature)}${unit.symbol}"
            Text(
                stringResource(R.string.weather_now, temp, stringResource(conditionName(Conditions.from(snapshot)))),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.weather_updated, DateFormat.getTimeFormat(context).format(Date(snapshot.fetchedAtMillis))),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.refresh_now)) }
    }
}

@Composable
private fun IconStyleChoice(style: IconStyle, onChange: (IconStyle) -> Unit) {
    Section(R.string.section_icons) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            IconStyle.entries.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = style == option,
                    onClick = { onChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(i, IconStyle.entries.size),
                ) {
                    Text(stringResource(if (option == IconStyle.NOTHING) R.string.icons_nothing else R.string.icons_original))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.icons_hint), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PageList(
    order: List<Page>,
    disabled: Set<Page>,
    onOrderChange: (List<Page>) -> Unit,
    onDisabledChange: (Set<Page>) -> Unit,
) {
    order.forEachIndexed { i, page ->
        val enabled = page !in disabled
        // The toy needs at least one page; don't let the last one be switched off.
        val lastEnabled = enabled && order.count { it !in disabled } == 1
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(page.title), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(page.summary), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { onOrderChange(order.swap(i, i - 1)) }, enabled = i > 0) {
                Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.move_up))
            }
            IconButton(onClick = { onOrderChange(order.swap(i, i + 1)) }, enabled = i < order.lastIndex) {
                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.move_down))
            }
            Switch(
                checked = enabled,
                enabled = !lastEnabled,
                onCheckedChange = { on -> onDisabledChange(if (on) disabled - page else disabled + page) },
            )
        }
    }
}

@Composable
private fun SwitchRow(@StringRes title: Int, @StringRes summary: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(summary), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Section(@StringRes title: Int, content: @Composable () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Increments whenever any value in these preferences changes, so readers recompose. */
@Composable
private fun SharedPreferences.changeCount(): Int {
    var count by remember { mutableIntStateOf(0) }
    DisposableEffect(this) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> count++ }
        registerOnSharedPreferenceChangeListener(listener)
        onDispose { unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return count
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> =
    toMutableList().also { it[a] = this[b]; it[b] = this[a] }

private val Page.title: Int
    get() = when (this) {
        Page.CURRENT -> R.string.page_current
        Page.ANIMATION -> R.string.page_animation
        Page.HI_LO -> R.string.page_hi_lo
        Page.RAIN -> R.string.page_rain
    }

private val Page.summary: Int
    get() = when (this) {
        Page.CURRENT -> R.string.page_current_summary
        Page.ANIMATION -> R.string.page_animation_summary
        Page.HI_LO -> R.string.page_hi_lo_summary
        Page.RAIN -> R.string.page_rain_summary
    }

private fun conditionName(condition: Condition): Int = when (condition) {
    Condition.CLEAR_DAY, Condition.CLEAR_NIGHT -> R.string.condition_clear
    Condition.PARTLY_CLOUDY_DAY, Condition.PARTLY_CLOUDY_NIGHT -> R.string.condition_partly_cloudy
    Condition.CLOUDY -> R.string.condition_cloudy
    Condition.FOG -> R.string.condition_fog
    Condition.DRIZZLE -> R.string.condition_drizzle
    Condition.RAIN -> R.string.condition_rain
    Condition.SLEET -> R.string.condition_sleet
    Condition.SNOW -> R.string.condition_snow
    Condition.THUNDERSTORM -> R.string.condition_thunderstorm
    Condition.WINDY -> R.string.condition_windy
}

/** Opens Nothing's Glyph Toys manager so the user can add this toy (system 20250829 or later). */
private fun openToysManager(context: Context) {
    val intent = Intent().setComponent(
        ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity"),
    )
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.activate_toy_failed, Toast.LENGTH_LONG).show()
    }
}
