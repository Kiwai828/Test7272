package com.recapmaker.app.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ══════════════════════════════════
// BASE PALETTE — dark theme foundation
// All semantic tokens below derive from these values, so there are no
// stray hardcoded hex literals scattered through the UI.
// ══════════════════════════════════

val Primary       = Color(0xFF7C6AFF)
val PrimaryDim    = Color(0xFF6C5CE7)
val PrimaryBg     = Color(0xFF1E1E30)
val Secondary     = Color(0xFF00E897)
val Tertiary      = Color(0xFFFFBE2E)
val Surface       = Color(0xFF0F1117)
val SurfaceCard   = Color(0xFF161A2A)
val SurfaceDark   = Color(0xFF0A0E1A)
val SurfaceVariant= Color(0xFF1E293B)
val OnSurface     = Color(0xFFE8ECF4)
val OnSurfaceDim  = Color(0xFF8A90AD)
val OnSurfaceMid  = Color(0xFFC4C8DD)
val Border        = Color(0xFF1E293B)
val BorderAccent  = Color(0xFF3A4463)
val Error         = Color(0xFFEF4444)
val OnError       = Color(0xFFffffff)
val Success       = Color(0xFF10B981)
val OnSuccess     = Color(0xFFffffff)
val Warning       = Color(0xFFFFA500)
val OnWarning     = Color(0xFF1a1200)
val Info          = Color(0xFF3B82F6)
val OnInfo        = Color(0xFFffffff)
val Rose          = Color(0xFFFF4F7B)
val Cyan          = Color(0xFF00E5FF)
val Gold          = Tertiary
val Silver        = Color(0xFFC0C0C0)
val OnPrimary     = Color.White
val Elevation     = Color(0x40000000)

// ── Light-mode counterparts (coherent semantic tokens for day mode) ──
internal val PrimaryLight      = Color(0xFF6548D9)
internal val PrimaryDimLight   = Color(0xFF4B36B3)
internal val PrimaryContainerLight = Color(0xFFE8E0FF)
internal val SecondaryLight    = Color(0xFF00B375)
internal val SecondaryContainerLight = Color(0xFFE0FFF6)
internal val TertiaryLight     = Color(0xFFE58A00)
internal val TertiaryContainerLight = Color(0xFFFFE8B3)
internal val SurfaceLight      = Color(0xFFFFFFFF)
internal val SurfaceContainerLight = Color(0xFFF4F5FA)
internal val SurfaceContainerLowLight = Color(0xFFF8F9FE)
internal val SurfaceVariantLight = Color(0xFFE2E3F2)
internal val OnSurfaceLight    = Color(0xFF1A1C2A)
internal val OnSurfaceDimLight = Color(0xFF7A7FA9)
internal val OnSurfaceMidLight = Color(0xFF4A4D6A)
internal val BorderLight       = Color(0xFFD9DCE8)
internal val BorderAccentLight = Color(0xFFB8BDF2)
internal val ErrorLight       = Color(0xFFB91C1C)
internal val SurfaceDarkLight  = Color(0xFFEEF1FB)

// ══════════════════════════════════
// COLOR SCHEMES (Material 3 semantic roles)
// Rich set of roles including tonal surface containers for elevation.
// ══════════════════════════════════

val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryDim,
    onPrimaryContainer = OnPrimary,
    inversePrimary = PrimaryDim,
    secondary = Secondary,
    onSecondary = Color(0xFF051E16),
    secondaryContainer = Secondary.copy(0.15f),
    onSecondaryContainer = Color(0xFF051E16),
    tertiary = Tertiary,
    onTertiary = Color(0xFF3A2A00),
    tertiaryContainer = Tertiary.copy(0.20f),
    onTertiaryContainer = Color(0xFF3A2A00),
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    surfaceDim = SurfaceDark,
    surfaceBright = Surface,
    surfaceContainer = SurfaceCard,
    surfaceContainerLow = SurfaceCard.copy(0.92f),
    surfaceContainerHigh = SurfaceCard.copy(1.08f),
    surfaceContainerHighest = SurfaceCard.copy(1.14f),
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceDim,
    surfaceVariant = SurfaceVariant,
    outline = BorderAccent,
    outlineVariant = Border,
    error = Error,
    onError = OnError,
    errorContainer = Error.copy(0.15f),
    onErrorContainer = Error,
    inverseOnSurface = OnSurface,
    inverseSurface = SurfaceCard,
    scrim = Color(0x80000000),
)

val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = Color(0xFF230065),
    inversePrimary = PrimaryDimLight,
    secondary = SecondaryLight,
    onSecondary = Color(0xFF003221),
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = Color(0xFF003221),
    tertiary = TertiaryLight,
    onTertiary = Color(0xFF3A2A00),
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = Color(0xFF3D2700),
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    surfaceDim = SurfaceContainerLowLight,
    surfaceBright = SurfaceLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainerHigh = Color(0xFFE8EAF7),
    surfaceContainerHighest = Color(0xFFDDE1EF),
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceMidLight,
    surfaceVariant = SurfaceVariantLight,
    outline = BorderAccentLight,
    outlineVariant = BorderLight,
    error = ErrorLight,
    onError = OnError,
    errorContainer = ErrorLight.copy(0.12f),
    onErrorContainer = Color(0xFF450016),
    inverseOnSurface = OnSurfaceLight,
    inverseSurface = SurfaceContainerLight,
    scrim = Color(0x80000000),
)

// Backwards-compatible alias retained (used elsewhere by name).
val DarkScheme = DarkColorScheme

// ══════════════════════════════════
// SHAPES — rounded corners across cards, dialogs, inputs
// ══════════════════════════════════

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

// ══════════════════════════════════
// TYPOGRAPHY — refined scale, modern sans-serif stack
// ══════════════════════════════════

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp),
)

// ══════════════════════════════════
// LEGACY ALIASES — keep the original friendly names so existing
// screens/components keep compiling and their look is unchanged.
// Each alias points at an explicit semantic palette token.
// ══════════════════════════════════
val Purple        = Primary
val PurpleDark    = PrimaryDim
val Emerald       = Secondary
val DarkBg        = Surface
val CardBg        = SurfaceCard
val CardBorder    = Border
val TextPrimary   = OnSurface
val TextDim       = OnSurfaceDim
val TextMid       = OnSurfaceMid
val ErrorRed      = Error
val SilverColor   = Silver
val WarningYellow = Warning

// A few extra semantic tokens reused by components for state colors.
val OnSurfaceError = OnError
val OutlineVariant = Border

@Composable
fun RecapTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // `dynamicColor` is accepted for API completeness; the app relies on the
    // curated palette above so wall-to-wall dynamic colors are intentionally
    // disabled to keep brand consistency.
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
