package app.sona.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SonaColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    secondary = Accent,
    onSecondary = OnAccent,
    background = BgBase,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = Accent,
    onError = OnAccent,
)

@Composable
fun SonaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SonaColors, typography = SonaType, content = content)
}
