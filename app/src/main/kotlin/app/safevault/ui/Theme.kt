package app.safevault.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Brand: deep vault blue (matches the launcher icon) with a warm amber used only for "needs attention".
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F4E79),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2A5F94),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF4A6580),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE8F3),
    onSecondaryContainer = Color(0xFF0F2A44),
    tertiary = Color(0xFFB45309),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE8CC),
    onTertiaryContainer = Color(0xFF4A2300),
    background = Color(0xFFF4F7FA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFF4F7FA),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE6ECF2),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFF8794A3),
    outlineVariant = Color(0xFFD5DCE4),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEDF1F6),
    surfaceContainerHigh = Color(0xFFE8EDF3),
    surfaceContainerHighest = Color(0xFFDFE6EE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC7F0),
    onPrimary = Color(0xFF0B2F52),
    primaryContainer = Color(0xFF24527F),
    onPrimaryContainer = Color(0xFFE6F1FC),
    secondary = Color(0xFFB4C8DC),
    onSecondary = Color(0xFF1B3047),
    secondaryContainer = Color(0xFF2B3F52),
    onSecondaryContainer = Color(0xFFDCE8F3),
    tertiary = Color(0xFFF5B565),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF5A3300),
    onTertiaryContainer = Color(0xFFFFDDB5),
    background = Color(0xFF0E1319),
    onBackground = Color(0xFFE4E9EF),
    surface = Color(0xFF0E1319),
    onSurface = Color(0xFFE4E9EF),
    surfaceVariant = Color(0xFF232C36),
    onSurfaceVariant = Color(0xFFA8B3BF),
    outline = Color(0xFF74808D),
    outlineVariant = Color(0xFF2C3844),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF7A1A14),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Color(0xFF0A0F14),
    surfaceContainerLow = Color(0xFF161E27),
    surfaceContainer = Color(0xFF1B242E),
    surfaceContainerHigh = Color(0xFF222C37),
    surfaceContainerHighest = Color(0xFF2A3541),
)

private val base = Typography()

// Slightly taller line heights so Thai tone marks and vowels are never clipped; no letter-spacing on labels.
private val SvTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 40.sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 36.sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.copy(lineHeight = 22.sp),
    bodySmall = base.bodySmall.copy(lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(lineHeight = 20.sp),
    labelMedium = base.labelMedium.copy(letterSpacing = 0.sp, lineHeight = 18.sp),
    labelSmall = base.labelSmall.copy(letterSpacing = 0.sp, lineHeight = 16.sp),
)

private val SvShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SafeVaultTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = scheme, typography = SvTypography, shapes = SvShapes, content = content)
}

@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun successColor(): Color = if (isDarkTheme()) Color(0xFF7BD389) else Color(0xFF1E7B3A)
