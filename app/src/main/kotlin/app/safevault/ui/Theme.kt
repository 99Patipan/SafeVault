package app.safevault.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── High-tech palette ────────────────────────────────────────────────────────
// Deep void black + electric cyan / electric blue accents.
// Amber kept only for "needs attention" states.

private val Cyan = Color(0xFF00E5FF)
private val NeonViolet = Color(0xFFB388FF)
private val VoidBlack = Color(0xFF05070A)
private val PanelDark = Color(0xFF111820)
private val PanelMid = Color(0xFF171F2A)
private val PanelLight = Color(0xFF1E2836)
private val GlassBorder = Color(0xFF2A3648)
private val SoftWhite = Color(0xFFE8F0F8)
private val MutedGray = Color(0xFF8A9BB0)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B7D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF00B8D4),
    onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF3D5A80),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4E4F7),
    onSecondaryContainer = Color(0xFF0A1A2E),
    tertiary = Color(0xFFB45309),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE8CC),
    onTertiaryContainer = Color(0xFF4A2300),
    background = Color(0xFFF0F4F8),
    onBackground = Color(0xFF0A121C),
    surface = Color(0xFFF0F4F8),
    onSurface = Color(0xFF0A121C),
    surfaceVariant = Color(0xFFE0E8F0),
    onSurfaceVariant = Color(0xFF4A5A6A),
    outline = Color(0xFF7A8A9A),
    outlineVariant = Color(0xFFC8D4E0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFE8EEF4),
    surfaceContainerHigh = Color(0xFFE0E8F0),
    surfaceContainerHighest = Color(0xFFD4DCE6),
)

private val DarkColors = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF003640),
    primaryContainer = Color(0xFF004D5C),
    onPrimaryContainer = Color(0xFFB8F5FF),
    secondary = NeonViolet,
    onSecondary = Color(0xFF1A0A3A),
    secondaryContainer = Color(0xFF2A1F4A),
    onSecondaryContainer = Color(0xFFE8DFFF),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF3A2200),
    tertiaryContainer = Color(0xFF5A3300),
    onTertiaryContainer = Color(0xFFFFDDB5),
    background = VoidBlack,
    onBackground = SoftWhite,
    surface = VoidBlack,
    onSurface = SoftWhite,
    surfaceVariant = PanelMid,
    onSurfaceVariant = MutedGray,
    outline = Color(0xFF4A5A6E),
    outlineVariant = GlassBorder,
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3A0000),
    errorContainer = Color(0xFF7A1A14),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Color(0xFF030507),
    surfaceContainerLow = PanelDark,
    surfaceContainer = PanelMid,
    surfaceContainerHigh = PanelLight,
    surfaceContainerHighest = Color(0xFF263040),
)

private val base = Typography()

// Slightly taller line heights so Thai tone marks and vowels are never clipped.
private val SvTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 40.sp),
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
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
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
fun successColor(): Color = if (isDarkTheme()) Color(0xFF69F0AE) else Color(0xFF00C853)

/** Soft cyan→blue gradient used on hero cards and brand accents. */
@Composable
fun accentGradient(): Brush = Brush.linearGradient(
    colors = listOf(
        if (isDarkTheme()) Color(0xFF00E5FF) else Color(0xFF00B8D4),
        if (isDarkTheme()) Color(0xFF2979FF) else Color(0xFF1565C0),
    ),
)

/** Subtle glass-style border for dark mode cards. */
@Composable
fun glassBorderColor(): Color =
    if (isDarkTheme()) Color(0xFF2A3648) else MaterialTheme.colorScheme.outlineVariant
