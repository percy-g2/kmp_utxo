package theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import getKStore
import kotlinx.coroutines.flow.MutableStateFlow
import ui.Settings
import ui.Theme

val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = BlueLighter80,
    tertiary = BlueGrey80,
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = DarkBlue80,
    onBackground = Color(0xFFF9F7F7),
    onSurface = Color(0xFFF9F7F7),
    primaryContainer = DarkBlue80,
    onPrimaryContainer = Color.White
)

val LightColorScheme = lightColorScheme(
    primary = Blue40,
    secondary = BlueLighter40,
    tertiary = BlueGrey40,
    background = Color(0xFFF9F7F7),
    surface = Color(0xFFF9F7F7),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = DarkBlue40,
    onSurface = DarkBlue40,
    primaryContainer = BlueLighter40,
    onPrimaryContainer = Color.White
)


@Composable
fun UTXOTheme(
    colorScheme: ColorScheme,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

object ThemeManager {
    val themeState = MutableStateFlow(Theme.SYSTEM.id)
    val store = getKStore()

    suspend fun updateTheme(newTheme: Int) {
        themeState.value = newTheme
        store.update { it?.copy(selectedTheme = newTheme) ?: Settings(selectedTheme = newTheme) }
    }
}