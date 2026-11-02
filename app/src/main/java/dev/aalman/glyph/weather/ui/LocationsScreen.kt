package dev.aalman.glyph.weather.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.aalman.glyph.weather.R
import dev.aalman.glyph.weather.data.City
import dev.aalman.glyph.weather.data.IconStyle
import dev.aalman.glyph.weather.data.OpenMeteo
import dev.aalman.glyph.weather.data.TemperatureUnit
import dev.aalman.glyph.weather.icons.Condition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** What a location card shows: the city's current hour from its stored forecast. */
data class LocationCard(val city: City, val temperature: Double?, val condition: Condition?, val selected: Boolean)

/** How long a card must be held before the list switches to reordering. */
private const val HOLD_MILLIS = 1_000L
private val CARD_HEIGHT = 84.dp
private val GAP = 8.dp
private val FIELD = Color(0xFF1C1C1C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsScreen(
    cards: List<LocationCard>,
    searchCountry: String?,
    unit: TemperatureUnit,
    style: IconStyle,
    onBack: () -> Unit,
    onSelect: (City) -> Unit,
    onRemove: (City) -> Unit,
    onRestore: (City, Int) -> Unit,
    onReorder: (List<City>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var reordering by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removedText = stringResource(R.string.location_removed)
    val undoText = stringResource(R.string.undo)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (reordering) TextButton(onClick = { reordering = false }) { Text(stringResource(R.string.done)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Text(
                stringResource(R.string.locations_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
            )
            if (!reordering) SearchField(query) { query = it }

            if (query.isNotBlank() && !reordering) {
                val countries = remember(cards) {
                    cards.mapNotNull { it.city.countryCode }.toSet()
                }
                val regions = remember(cards) { cards.mapNotNull { it.city.admin1 }.toSet() }
                SearchResults(query, searchCountry, countries, regions) { city ->
                    query = ""
                    onSelect(city)
                }
            } else if (reordering) {
                ReorderList(cards, unit, style, onReorder)
            } else {
                if (cards.isEmpty()) {
                    Text(stringResource(R.string.locations_empty), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
                }
                cards.forEachIndexed { index, card ->
                    key(card.city.key) {
                        SwipeToRemove(
                            onRemove = {
                                onRemove(card.city)
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    val result = snackbar.showSnackbar(
                                        removedText.format(card.city.name), undoText, duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) onRestore(card.city, index)
                                }
                            },
                        ) {
                            HoldableCard(
                                onTap = { onSelect(card.city) },
                                onHold = { reordering = true },
                            ) { CardContent(card, unit, style, trailing = null) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Clear, stringResource(R.string.clear)) }
        },
        singleLine = true,
        shape = RoundedCornerShape(50),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = FIELD,
            unfocusedContainerColor = FIELD,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Open-Meteo's city search, run once typing pauses, with places in [searchCountry] first. */
@Composable
private fun SearchResults(
    query: String,
    searchCountry: String?,
    preferredCountries: Set<String>,
    preferredRegions: Set<String>,
    onPick: (City) -> Unit,
) {
    var results by remember { mutableStateOf<List<City>>(emptyList()) }
    var status by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList()
            status = null
            return@LaunchedEffect
        }
        delay(400) // a new keystroke cancels this effect
        status = R.string.searching
        try {
            results = withContext(Dispatchers.IO) { OpenMeteo().searchCities(query, searchCountry, preferredCountries, preferredRegions) }
            status = if (results.isEmpty()) R.string.search_no_results else null
        } catch (e: IOException) {
            status = R.string.search_failed
        }
    }
    status?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(4.dp)) }
    for (city in results) {
        Text(
            city.label,
            Modifier
                .fillMaxWidth()
                .clip24()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { onPick(city) }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToRemove(onRemove: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onRemove()
            value == SwipeToDismissBoxValue.EndToStart
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip24()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Default.Delete, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.onErrorContainer) }
        },
    ) { content() }
}

/**
 * A card that reports a quick tap, or a hold of [HOLD_MILLIS]. An outline fills in while it is
 * held; moving the finger (scrolling or swiping) cancels the hold.
 */
@Composable
private fun HoldableCard(onTap: () -> Unit, onHold: () -> Unit, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT)
            .clip24()
            .background(FIELD)
            .border(2.dp, Color.White.copy(alpha = progress.value), RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val started = System.currentTimeMillis()
                    val fill = scope.launch { progress.animateTo(1f, tween(HOLD_MILLIS.toInt(), easing = LinearEasing)) }
                    // true = lifted, false = moved away, null = still held after HOLD_MILLIS.
                    val outcome = withTimeoutOrNull(HOLD_MILLIS) {
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.first()
                            if (!change.pressed) return@withTimeoutOrNull true
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull false
                        }
                        @Suppress("UNREACHABLE_CODE") false
                    }
                    fill.cancel()
                    scope.launch { progress.snapTo(0f) }
                    when (outcome) {
                        null -> {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onHold()
                        }
                        true -> if (System.currentTimeMillis() - started < 500) onTap()
                        false -> Unit
                    }
                }
            },
    ) { content() }
}

@Composable
private fun CardContent(card: LocationCard, unit: TemperatureUnit, style: IconStyle, trailing: (@Composable () -> Unit)?) {
    Row(
        Modifier
            .fillMaxSize()
            .then(if (card.selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)) else Modifier)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            card.condition?.let { SmallIcon(it, style, Modifier.size(32.dp)) }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(card.city.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = listOfNotNull(
                card.city.admin1?.takeIf { it != card.city.name },
                card.condition?.let { stringResource(conditionName(it)) },
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Text(card.temperature?.let { "${unit.display(it)}°" } ?: "–", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

/** How long a dragged card must stay over another card's place before the cards make room. */
private const val SETTLE_MILLIS = 500L

/**
 * Cards with drag handles. The dragged card follows the finger but stays between the first and
 * last cards' places. When it has stayed over another card's place for [SETTLE_MILLIS], the
 * cards in between slide over to make room. Releasing drops it into the place it's over, and the
 * new order is saved.
 *
 * While dragging, the list order doesn't change: the other cards are only drawn shifted. The
 * order is updated once, on release, in the same frame that the shifts are cleared, so no card
 * jumps or bounces.
 */
@Composable
private fun ReorderList(cards: List<LocationCard>, unit: TemperatureUnit, style: IconStyle, onReorder: (List<City>) -> Unit) {
    var order by remember(cards.map { it.city.key }) { mutableStateOf(cards) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var from by remember { mutableIntStateOf(-1) }
    // The place the other cards currently make room at, after the settle delay.
    var settled by remember { mutableIntStateOf(-1) }
    // The dragged card's top edge, measured from the first card's resting place.
    var dragY by remember { mutableFloatStateOf(0f) }
    // Each drag gets fresh slide animations, so nothing carries over from the last one.
    var session by remember { mutableIntStateOf(0) }
    val step = with(LocalDensity.current) { (CARD_HEIGHT + GAP).toPx() }
    val scope = rememberCoroutineScope()
    var settle by remember { mutableStateOf<Job?>(null) }
    var pending by remember { mutableIntStateOf(-1) }

    fun target() = kotlin.math.round(dragY / step).toInt().coerceIn(0, order.lastIndex)
    fun scheduleSettle() {
        val target = target()
        if (target == settled) {
            settle?.cancel()
            pending = -1
        } else if (target != pending) {
            settle?.cancel()
            pending = target
            settle = scope.launch {
                delay(SETTLE_MILLIS)
                settled = target
                pending = -1
            }
        }
    }
    fun drop() {
        settle?.cancel()
        pending = -1
        val to = target()
        if (from in order.indices && to != from) order = order.toMutableList().apply { add(to, removeAt(from)) }
        dragging = null
        from = -1
        settled = -1
        session++
        onReorder(order.map { it.city })
    }
    /** How many places card [i] is drawn away from its own while the others make room. */
    fun shiftOf(i: Int): Int = when {
        dragging == null || i == from -> 0
        from < settled && i in (from + 1)..settled -> -1
        settled < from && i in settled until from -> 1
        else -> 0
    }

    order.forEachIndexed { index, card ->
        key(card.city.key) {
            val active = dragging == card.city.key
            val offset = remember(session) { Animatable(0f) }
            val goal = shiftOf(index) * step
            LaunchedEffect(goal, session) { offset.animateTo(goal, tween(200)) }
            Box(
                Modifier
                    .zIndex(if (active) 1f else 0f)
                    .graphicsLayer {
                        translationY = when {
                            dragging == null -> 0f
                            active -> dragY - index * step
                            else -> offset.value
                        }
                    }
                    .fillMaxWidth()
                    .height(CARD_HEIGHT)
                    .clip24()
                    .background(if (active) MaterialTheme.colorScheme.surfaceContainerHigh else FIELD),
            ) {
                CardContent(card, unit, style) {
                    Icon(
                        Icons.Default.Menu,
                        stringResource(R.string.drag_to_reorder),
                        Modifier
                            .size(40.dp)
                            .padding(8.dp)
                            .pointerInput(card.city.key) {
                                detectDragGestures(
                                    onDragStart = {
                                        val i = order.indexOfFirst { it.city.key == card.city.key }
                                        dragging = card.city.key
                                        from = i
                                        settled = i
                                        dragY = i * step
                                    },
                                    onDragEnd = { drop() },
                                    onDragCancel = { drop() },
                                ) { change, amount ->
                                    change.consume()
                                    dragY = (dragY + amount.y).coerceIn(0f, order.lastIndex * step)
                                    scheduleSettle()
                                }
                            },
                    )
                }
            }
        }
    }
}

private fun Modifier.clip24() = clip(RoundedCornerShape(24.dp))
