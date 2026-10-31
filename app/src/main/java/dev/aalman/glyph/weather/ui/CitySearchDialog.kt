package dev.aalman.glyph.weather.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.aalman.glyph.weather.R
import dev.aalman.glyph.weather.data.City
import dev.aalman.glyph.weather.data.OpenMeteo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException

/** Search-as-you-type city picker backed by Open-Meteo's geocoding API. */
@Composable
fun CitySearchDialog(onPick: (City) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<City>>(emptyList()) }
    var status by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList()
            status = null
            return@LaunchedEffect
        }
        delay(400) // wait for typing to pause; a new keystroke cancels this effect
        status = R.string.searching
        try {
            results = withContext(Dispatchers.IO) { OpenMeteo().searchCities(query) }
            status = if (results.isEmpty()) R.string.search_no_results else null
        } catch (e: IOException) {
            status = R.string.search_failed
        } catch (e: SerializationException) {
            status = R.string.search_failed
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                status?.let { Text(stringResource(it), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall) }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results) { city ->
                        Text(
                            city.label,
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(city) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
