package cn.jiayi.familymemory.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF5F4B32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2E3CB),
    secondary = Color(0xFF52634F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E8D3),
    onSecondaryContainer = Color(0xFF162215),
    tertiary = Color(0xFF80553F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE9E2C3),
    onTertiaryContainer = Color(0xFF251A08),
    background = Color(0xFFFFF8F1),
    onBackground = Color(0xFF211A14),
    surface = Color(0xFFFFF8F1),
    onSurface = Color(0xFF211A14),
    surfaceVariant = Color(0xFFEDE1D4),
    onSurfaceVariant = Color(0xFF4D453D),
    error = Color(0xFF9B2C2C),
    onError = Color.White,
    outline = Color(0xFF7D7267),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD9C3A2),
    onPrimary = Color(0xFF382B18),
    primaryContainer = Color(0xFF46341F),
    onPrimaryContainer = Color(0xFFF5DFC0),
    secondary = Color(0xFFBACCB6),
    onSecondary = Color(0xFF263426),
    secondaryContainer = Color(0xFF354735),
    onSecondaryContainer = Color(0xFFD5E8D1),
    tertiary = Color(0xFFE6B9A1),
    onTertiary = Color(0xFF482A1D),
    tertiaryContainer = Color(0xFF5D3C2C),
    onTertiaryContainer = Color(0xFFFFDBCA),
    background = Color(0xFF1D1A17),
    onBackground = Color(0xFFEAE1D8),
    surface = Color(0xFF1D1A17),
    onSurface = Color(0xFFEAE1D8),
    surfaceVariant = Color(0xFF39332E),
    onSurfaceVariant = Color(0xFFD1C5BA),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    outline = Color(0xFF9E9186),
)

private val AccessibleTypography = Typography().let { base ->
    base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 23.sp),
        bodySmall = base.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = base.labelLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
        titleMedium = base.titleMedium.copy(fontSize = 19.sp, lineHeight = 26.sp),
        titleLarge = base.titleLarge.copy(fontSize = 23.sp, lineHeight = 30.sp),
    )
}

@Composable
fun FamilyMemoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        WindowCompat.getInsetsController((view.context as Activity).window, view).isAppearanceLightStatusBars = !darkTheme
    }
    MaterialTheme(colorScheme = colors, typography = AccessibleTypography, content = content)
}
