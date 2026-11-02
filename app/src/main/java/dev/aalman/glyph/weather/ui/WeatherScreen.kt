package dev.aalman.glyph.weather.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aalman.glyph.weather.R
import dev.aalman.glyph.weather.data.City
import dev.aalman.glyph.weather.data.Day
import dev.aalman.glyph.weather.data.Forecast
import dev.aalman.glyph.weather.data.Hour
import dev.aalman.glyph.weather.data.IconStyle
import dev.aalman.glyph.weather.data.SourceError
import dev.aalman.glyph.weather.data.TemperatureUnit
import dev.aalman.glyph.weather.data.WeatherSnapshot
import dev.aalman.glyph.weather.data.source.SourceId
import dev.aalman.glyph.weather.icons.Condition
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/** Card surfaces: a step up from the black background, matching the dialogs. */
val CARD = Color(0xFF121212)

/** Everything the Weather page shows, already filtered to the selected city and source. */
data class WeatherPageState(
    val city: City?,
    val snapshot: WeatherSnapshot?,
    val forecast: Forecast?,
    val error: SourceError?,
    val source: SourceId,
    val unit: TemperatureUnit,
    val style: IconStyle,
    val animate: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    state: WeatherPageState,
    onOpenLocations: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.city?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onOpenLocations) { Icon(Icons.Default.LocationOn, stringResource(R.string.locations_title)) }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings_title)) }
                },
            )
        },
    ) { padding ->
        if (state.city == null) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.no_city), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenLocations) { Text(stringResource(R.string.choose_city)) }
            }
            return@Scaffold
        }

        // Shows the refresh spinner until new data arrives, or for at most 15 seconds.
        var refreshing by remember { mutableStateOf(false) }
        LaunchedEffect(state.snapshot?.fetchedAtMillis, state.error) { refreshing = false }
        LaunchedEffect(refreshing) { if (refreshing) { delay(15_000); refreshing = false } }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; onRefresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val scroll = rememberScrollState()
                // The hero matrix and its margin at full size; scrolling down shrinks both
                // before the cards move, so the figures above stay on screen.
                val heroFull = with(LocalDensity.current) { (maxWidth - 40.dp).toPx() * HERO_WIDTH + 16.dp.toPx() }
                val collapseRange = heroFull * (1f - HERO_MIN_SCALE)
                var collapsed by rememberSaveable { mutableFloatStateOf(0f) }
                val connection = remember(collapseRange) {
                    object : NestedScrollConnection {
                        // Finger moving up: shrink the hero first, then let the cards scroll.
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (available.y >= 0) return Offset.Zero
                            // Shrinking grows the cards' viewport, so stop once the cards fit:
                            // the page ends with the last card at the bottom of the screen.
                            val limit = minOf(collapseRange, collapsed + scroll.maxValue)
                            val next = (collapsed - available.y).coerceAtMost(limit).coerceAtLeast(collapsed)
                            val used = next - collapsed
                            collapsed = next
                            return Offset(0f, -used)
                        }

                        // Finger moving down: once the cards are back at the top, grow the hero.
                        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                            if (available.y <= 0) return Offset.Zero
                            val next = (collapsed - available.y).coerceAtLeast(0f)
                            val used = collapsed - next
                            collapsed = next
                            return Offset(0f, used)
                        }
                    }
                }
                val scale = 1f - collapsed / heroFull

                Column(Modifier.fillMaxSize().nestedScroll(connection)) {
                    // Drags on the pinned header move the same scroll as the cards.
                    Column(
                        Modifier
                            .scrollable(scroll, Orientation.Vertical, reverseDirection = true)
                            .padding(horizontal = 20.dp),
                    ) {
                        CurrentConditions(state, scale)
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scroll)
                            .padding(start = 20.dp, end = 20.dp, bottom = CARD_GAP),
                        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
                    ) {
                        WeatherCards(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherCards(state: WeatherPageState) {
    state.forecast?.let { forecast ->
        val zone = state.city!!.zone
        val now = System.currentTimeMillis()
        TodayCard(forecast.hoursFrom(now), zone, state.unit, state.style)
        val days = forecast.days(zone, now)
        if (days.isNotEmpty()) NextDaysCard(days, forecast.hourAt(now)?.temperature, zone, state.unit, state.style)
    }
}

@Composable
private fun CurrentConditions(state: WeatherPageState, scale: Float) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val zone = state.city!!.zone
    // Between selecting a city and its first refresh, show its stored forecast's current hour.
    val hour = state.forecast?.hourAt(now)
    val temperature = state.snapshot?.temperature ?: hour?.temperature
    val condition = state.snapshot?.condition ?: hour?.condition
    val today = state.forecast?.days(zone, now)?.firstOrNull()
    val high = state.snapshot?.todayHigh ?: today?.high
    val low = state.snapshot?.todayLow ?: today?.low

    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            temperature?.let { "${state.unit.display(it)}${state.unit.symbol}" } ?: "–",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            if (condition != null) Text(stringResource(conditionName(condition)), style = MaterialTheme.typography.titleMedium)
            if (high != null && low != null) {
                Row {
                    Text(stringResource(R.string.high_value, state.unit.display(high)), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.low_value, state.unit.display(low)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    val updated = state.snapshot?.fetchedAtMillis ?: state.forecast?.fetchedAtMillis
    if (updated != null) {
        Text(
            stringResource(
                R.string.weather_updated,
                stringResource(sourceName(state.source)),
                DateFormat.getTimeFormat(context).format(Date(updated)),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(stringResource(R.string.weather_waiting), style = MaterialTheme.typography.bodySmall)
    }
    state.error?.let { error ->
        Text(
            stringResource(
                R.string.weather_error,
                DateFormat.getTimeFormat(context).format(Date(error.atMillis)),
                stringResource(errorMessage(error.kind)),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    if (condition != null) {
        val animation = remember(condition, state.style, temperature?.let { it >= 40 || it <= -10 }) {
            IconArt.full(condition, state.style, temperature)
        }
        val frame = animatedFrame(animation, state.animate)
        Box(Modifier.fillMaxWidth().padding(vertical = (8 * scale).dp), contentAlignment = Alignment.Center) {
            DotFrame(frame, Modifier.fillMaxWidth(HERO_WIDTH * scale).aspectRatio(1f), showUnlit = true)
        }
    }
}

/** The hero matrix's width as a share of the page, and its smallest size once scrolled. */
private const val HERO_WIDTH = 0.72f
private const val HERO_MIN_SCALE = 0.35f

/** Space between the cards, and below the last one. */
private val CARD_GAP = 16.dp

private const val COLUMN_DP = 60

/** The coming hours: temperature, icon and rain chance above a temperature line. */
@Composable
private fun TodayCard(hours: List<Hour>, zone: java.time.ZoneId, unit: TemperatureUnit, style: IconStyle) {
    val context = LocalContext.current
    val today = LocalDate.ofInstant(Instant.now(), zone)
    // The rest of today, and at least 8 hours so late evenings still show something useful.
    val shown = hours.withIndex()
        .takeWhile { (i, h) -> i < 8 || LocalDate.ofInstant(Instant.ofEpochMilli(h.epochMillis), zone) == today }
        .map { it.value }
    if (shown.isEmpty()) return
    val is24 = DateFormat.is24HourFormat(context)

    Card(R.string.today_title) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Column(Modifier.width((COLUMN_DP * shown.size).dp)) {
                Row {
                    for (h in shown) {
                        Text(
                            "${unit.display(h.temperature)}°",
                            Modifier.width(COLUMN_DP.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Row(Modifier.padding(vertical = 6.dp)) {
                    for (h in shown) {
                        Box(Modifier.width(COLUMN_DP.dp), contentAlignment = Alignment.Center) {
                            SmallIcon(h.condition, style, Modifier.size(24.dp))
                        }
                    }
                }
                Row {
                    for (h in shown) {
                        Text(
                            if (h.precipitation > 0) "${h.precipitation}%" else "",
                            Modifier.width(COLUMN_DP.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = RAIN,
                        )
                    }
                }
                TemperatureLine(shown.map { it.temperature }, Modifier.fillMaxWidth().height(64.dp).padding(vertical = 8.dp))
                Row {
                    shown.forEachIndexed { i, h ->
                        val hour = Instant.ofEpochMilli(h.epochMillis).atZone(zone).hour
                        Text(
                            when {
                                i == 0 -> stringResource(R.string.now)
                                is24 -> "%02d".format(hour)
                                else -> "${(hour + 11) % 12 + 1}${if (hour < 12) "am" else "pm"}"
                            },
                            Modifier.width(COLUMN_DP.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private val RAIN = Color(0xFF8AB4F8)

/** A smooth line through [temps], one point per column, scaled to their own range. */
@Composable
private fun TemperatureLine(temps: List<Double>, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        if (temps.isEmpty()) return@Canvas
        val step = size.width / temps.size
        val min = temps.min()
        val range = (temps.max() - min).takeIf { it > 0.5 } ?: 0.5
        val points = temps.mapIndexed { i, t ->
            Offset(step * (i + 0.5f), size.height * (1f - ((t - min) / range).toFloat()))
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]
                val midX = (a.x + b.x) / 2
                cubicTo(midX, a.y, midX, b.y, b.x, b.y)
            }
        }
        drawPath(path, line.copy(alpha = 0.6f), style = Stroke(width = 2.dp.toPx()))
        points.forEachIndexed { i, p -> drawCircle(if (i == 0) line else line.copy(alpha = 0.8f), 3.dp.toPx(), p) }
    }
}

/** One row per day: name, icon, rain chance, low, a dot range bar, high. */
@Composable
private fun NextDaysCard(days: List<Day>, current: Double?, zone: java.time.ZoneId, unit: TemperatureUnit, style: IconStyle) {
    val weekLow = days.minOf { it.low }
    val weekHigh = days.maxOf { it.high }
    Card(R.string.next_days_title) {
        days.forEachIndexed { i, day ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (i == 0) stringResource(R.string.today) else day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    Modifier.width(56.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                SmallIcon(day.condition, style, Modifier.size(22.dp))
                Text(
                    if (day.maxPrecipitation > 0) "${day.maxPrecipitation}%" else "",
                    Modifier.width(44.dp).padding(start = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = RAIN,
                )
                Text("${unit.display(day.low)}°", Modifier.width(36.dp), textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                RangeDots(weekLow, weekHigh, day.low, day.high, if (i == 0) current else null,
                    Modifier.weight(1f).height(12.dp).padding(horizontal = 10.dp))
                Text("${unit.display(day.high)}°", Modifier.width(36.dp))
            }
        }
    }
}

/**
 * A row of dots spanning the week's range; dots inside the day's range are lit, and the dot
 * nearest [current] is drawn brighter and larger.
 */
@Composable
private fun RangeDots(weekLow: Double, weekHigh: Double, low: Double, high: Double, current: Double?, modifier: Modifier) {
    val lit = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val count = 14
        val pitch = size.width / count
        val span = (weekHigh - weekLow).takeIf { it > 0 } ?: 1.0
        fun position(t: Double) = ((t - weekLow) / span * (count - 1)).toFloat()
        val from = position(low)
        val to = position(high)
        val now = current?.let { kotlin.math.round(position(it)).toInt() }
        for (i in 0 until count) {
            val center = Offset(pitch * (i + 0.5f), size.height / 2)
            val on = i >= kotlin.math.floor(from) && i <= kotlin.math.ceil(to)
            when {
                i == now -> drawCircle(Color.White, pitch * 0.42f, center)
                on -> drawCircle(lit.copy(alpha = 0.75f), pitch * 0.3f, center)
                else -> drawCircle(UNLIT, pitch * 0.3f, center)
            }
        }
    }
}

@Composable
private fun Card(title: Int, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CARD, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

