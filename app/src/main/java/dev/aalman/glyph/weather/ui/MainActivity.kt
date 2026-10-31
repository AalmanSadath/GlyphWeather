package dev.aalman.glyph.weather.ui

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.aalman.glyph.weather.data.Refresh

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
                SettingsScreen()
            }
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
