package kz.duken.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1F4E8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF8A5A00),
    error = Color(0xFFB3261E),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00468C),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFFFB951),
    error = Color(0xFFF2B8B5),
    background = Color(0xFF121316),
    surface = Color(0xFF121316),
)

/** Цвета фиксированные (без Material You), чтобы скриншоты в тестах не зависели от обоев устройства. */
@Composable
fun DukenTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
