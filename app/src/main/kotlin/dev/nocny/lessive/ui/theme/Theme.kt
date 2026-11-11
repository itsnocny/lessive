package dev.nocny.lessive.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette for devices without dynamic color: a calm "clean water" blue.
private val Light = lightColorScheme(
    primary = Color(0xFF3B5BA9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDAE2FF),
    onPrimaryContainer = Color(0xFF0F2D6E),
    secondary = Color(0xFF56607B),
    secondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFF00696E),
    tertiaryContainer = Color(0xFF9CF1F6),
    surface = Color(0xFFFAF8FF),
    surfaceContainer = Color(0xFFEEEDF4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB2C5FF),
    onPrimary = Color(0xFF0F2D6E),
    primaryContainer = Color(0xFF284585),
    onPrimaryContainer = Color(0xFFDAE2FF),
    secondary = Color(0xFFBEC6DC),
    secondaryContainer = Color(0xFF3E4759),
    tertiary = Color(0xFF80D4DA),
    tertiaryContainer = Color(0xFF004F53),
    surface = Color(0xFF121318),
    surfaceContainer = Color(0xFF1E1F25),
)

@Composable
fun LessiveTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
