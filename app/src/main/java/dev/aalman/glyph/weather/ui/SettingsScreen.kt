package dev.aalman.glyph.weather.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.aalman.glyph.weather.BuildConfig
import dev.aalman.glyph.weather.R
import dev.aalman.glyph.weather.art.NothingArt
import dev.aalman.glyph.weather.data.IconStyle
import dev.aalman.glyph.weather.data.Page
import dev.aalman.glyph.weather.data.Settings
import dev.aalman.glyph.weather.data.TemperatureUnit
import dev.aalman.glyph.weather.data.source.SourceId

/**
 * Settings, in order of how often they're changed: where the weather comes from, units, the
 * Glyph Toy, then credits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: AppState, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = app.settings
    // Child composables take plain values, not these objects: Compose skips a child whose
    // arguments are the same instances as last time, so it would miss the change.
    settings.prefs.changeCount()
    var askingKey by remember { mutableStateOf(false) }
    var pickingCountry by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SourceChoice(
                source = settings.source,
                refreshMinutes = settings.refreshMinutes,
                savedCount = settings.savedCities.size,
                onRefreshChange = { settings.refreshMinutes = it },
                hasKey = settings.tomorrowKey != null,
                onPick = { picked ->
                    if (picked == SourceId.TOMORROW_IO && settings.tomorrowKey == null) askingKey = true else app.useSource(picked)
                },
                onChangeKey = { askingKey = true },
                onRemoveKey = app::removeTomorrowKey,
            )

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

            Section(R.string.section_search) {
                Text(stringResource(R.string.search_country_hint), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { pickingCountry = true }, Modifier.fillMaxWidth()) {
                    Text(countryLabel(settings.searchCountry))
                }
            }

            Section(R.string.section_toy) {
                Text(stringResource(R.string.toy_hint), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { openToysManager(context) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.activate_toy))
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.section_pages), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.pages_hint), style = MaterialTheme.typography.bodySmall)
                PageList(
                    order = settings.pageOrder,
                    disabled = settings.disabledPages,
                    onOrderChange = { settings.pageOrder = it },
                    onDisabledChange = { settings.disabledPages = it },
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.section_animation), style = MaterialTheme.typography.titleSmall)
                SwitchRow(R.string.animate, R.string.animate_summary, settings.animate) { settings.animate = it }
                SwitchRow(R.string.animate_aod, R.string.animate_aod_summary, settings.animateAod) { settings.animateAod = it }
            }

            if (NothingArt.available) {
                IconStyleChoice(settings.iconStyle) { settings.iconStyle = it }
            }

            Section(R.string.section_about) {
                val (credit, site) = when (settings.source) {
                    SourceId.OPEN_METEO -> R.string.attribution to "https://open-meteo.com/"
                    SourceId.TOMORROW_IO -> R.string.attribution_tomorrow to "https://www.tomorrow.io/"
                }
                TextButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(site))) },
                    contentPadding = PaddingValues(0.dp),
                ) { Text(stringResource(credit)) }
                if (NothingArt.available) {
                    Text(stringResource(R.string.art_credit), style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    stringResource(R.string.version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (pickingCountry) {
        CountryPickerDialog(
            onPick = { code ->
                settings.searchCountry = code
                pickingCountry = false
            },
            onDismiss = { pickingCountry = false },
        )
    }

    if (askingKey) {
        TomorrowKeyDialog(
            city = settings.city,
            onAccepted = { key, result ->
                askingKey = false
                app.acceptTomorrowKey(key, result)
            },
            onDismiss = { askingKey = false },
        )
    }
}

@Composable
private fun SourceChoice(
    source: SourceId,
    refreshMinutes: Int,
    savedCount: Int,
    onRefreshChange: (Int) -> Unit,
    hasKey: Boolean,
    onPick: (SourceId) -> Unit,
    onChangeKey: () -> Unit,
    onRemoveKey: () -> Unit,
) {
    Section(R.string.section_source) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SourceId.entries.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = source == option,
                    onClick = { onPick(option) },
                    shape = SegmentedButtonDefaults.itemShape(i, SourceId.entries.size),
                ) { Text(stringResource(sourceName(option))) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (source == SourceId.TOMORROW_IO) {
                stringResource(R.string.source_tomorrow_hint, 24 * 60 / refreshMinutes + (savedCount - 1).coerceAtLeast(0))
            } else {
                stringResource(R.string.source_open_meteo_hint)
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.refresh_every), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Settings.REFRESH_CHOICES.forEachIndexed { i, minutes ->
                SegmentedButton(
                    selected = refreshMinutes == minutes,
                    onClick = { onRefreshChange(minutes) },
                    shape = SegmentedButtonDefaults.itemShape(i, Settings.REFRESH_CHOICES.size),
                ) { Text(stringResource(R.string.refresh_minutes, minutes)) }
            }
        }
        Text(stringResource(R.string.refresh_hint), style = MaterialTheme.typography.bodySmall)
        if (hasKey) {
            Row {
                TextButton(onClick = onChangeKey) { Text(stringResource(R.string.tomorrow_key_change)) }
                TextButton(onClick = onRemoveKey) { Text(stringResource(R.string.tomorrow_key_remove)) }
            }
        }
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

/** "Australia", or "Automatic (Australia)" when [code] is null and the phone's country is used. */
@Composable
private fun countryLabel(code: String?): String {
    if (code != null) return java.util.Locale("", code).displayCountry
    val phone = java.util.Locale.getDefault().country
    val name = if (phone.length == 2) java.util.Locale("", phone).displayCountry else null
    return if (name != null) stringResource(R.string.search_country_auto_named, name) else stringResource(R.string.search_country_auto)
}

/** Every country, searchable by name, with "Automatic" first. Picks an ISO code or null. */
@Composable
private fun CountryPickerDialog(onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    val countries = remember {
        java.util.Locale.getISOCountries()
            .map { it to java.util.Locale("", it).displayCountry }
            .sortedBy { it.second }
    }
    val shown = countries.filter { filter.isBlank() || it.second.contains(filter.trim(), ignoreCase = true) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_country_title)) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.lazy.LazyColumn(Modifier.height(360.dp)) {
                    if (filter.isBlank()) {
                        item {
                            Text(
                                countryLabel(null),
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(null) }
                                    .padding(vertical = 14.dp),
                            )
                        }
                    }
                    items(shown.size) { i ->
                        val (code, name) = shown[i]
                        Text(
                            name,
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(code) }
                                .padding(vertical = 14.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
