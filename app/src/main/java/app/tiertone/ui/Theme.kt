package app.tiertone.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.tiertone.core.Lane

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF6D5BD0),
    background = Color(0xFFFBFBFE),
    surface = Color(0xFFFBFBFE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFC4B5FD),
)

@Composable
fun TierToneTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(LocalContext.current)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(LocalContext.current)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Fixed, meaningful colours per lane, the same whatever the wallpaper colours are. */
@Composable
fun laneColor(lane: Lane): Color {
    val dark = isSystemInDarkTheme()
    return when (lane) {
        Lane.VIP -> if (dark) Color(0xFFFBBF24) else Color(0xFFD97706)
        Lane.IMPORTANT -> if (dark) Color(0xFFFB7185) else Color(0xFFE11D48)
        Lane.NORMAL -> if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB)
        Lane.MUTED -> if (dark) Color(0xFF94A3B8) else Color(0xFF64748B)
        Lane.UNTOUCHED -> if (dark) Color(0xFF6B7280) else Color(0xFF9CA3AF)
    }
}
