package dev.aalman.glyph.weather.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.aalman.glyph.weather.R
import dev.aalman.glyph.weather.data.City
import dev.aalman.glyph.weather.data.FetchResult
import dev.aalman.glyph.weather.data.source.SourceException
import dev.aalman.glyph.weather.data.source.TomorrowIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Used to check a key when no city has been chosen yet. */
private val TEST_CITY = City("London", latitude = 51.5074, longitude = -0.1278, timezone = "Europe/London")

/**
 * Asks for a Tomorrow.io API key and checks it with one forecast request before accepting it.
 * [onAccepted] gets the key and, when [city] is set, the forecast the check downloaded.
 */
@Composable
fun TomorrowKeyDialog(city: City?, onAccepted: (key: String, result: FetchResult?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tomorrow_key_title)) },
        text = {
            Column {
                Text(stringResource(R.string.tomorrow_key_hint), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://app.tomorrow.io/development/keys")))
                }) { Text(stringResource(R.string.tomorrow_key_get)) }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim(); status = null },
                    label = { Text(stringResource(R.string.tomorrow_key_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                status?.let { Text(stringResource(it), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = key.isNotEmpty() && !checking,
                onClick = {
                    checking = true
                    status = R.string.tomorrow_key_checking
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) { TomorrowIo(key).fetch(city ?: TEST_CITY) }
                            onAccepted(key, result.takeIf { city != null })
                        } catch (e: SourceException) {
                            status = errorMessage(e.kind)
                        } finally {
                            checking = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.tomorrow_key_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** What to tell the user about a failed Tomorrow.io or Open-Meteo request. */
fun errorMessage(kind: SourceException.Kind): Int = when (kind) {
    SourceException.Kind.BAD_KEY -> R.string.error_bad_key
    SourceException.Kind.RATE_LIMITED -> R.string.error_rate_limited
    SourceException.Kind.NETWORK -> R.string.error_network
    SourceException.Kind.BAD_RESPONSE -> R.string.error_bad_response
}
