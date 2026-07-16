package cn.jiayi.familymemory.ui.theme

import android.app.Activity
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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF5F4B32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2E3CB),
    secondary = Color(0xFF52634F),
    tertiaryContainer = Color(0xFFE9E2C3),
    background = Color(0xFFFFF8F1),
    surface = Color(0xFFFFF8F1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD9C3A2),
    primaryContainer = Color(0xFF46341F),
    secondary = Color(0xFFBACCB6),
    background = Color(0xFF1C1B18),
    surface = Color(0xFF1C1B18),
)

@Composable
fun FamilyMemoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        WindowCompat.getInsetsController((view.context as Activity).window, view).isAppearanceLightStatusBars = !darkTheme
    }
    MaterialTheme(colorScheme = colors, content = content)
}
