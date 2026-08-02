package com.recapmaker.app.ui.common

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// ── Colors (modern dark Material 3) ──
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
val Success       = Color(0xFF10B981)
val Warning       = Color(0xFFFFA500)
val Info          = Color(0xFF3B82F6)
val Rose          = Color(0xFFFF4F7B)
val Cyan          = Color(0xFF00E5FF)
val Gold          = Tertiary
val Silver        = Color(0xFFC0C0C0)
val OnPrimary     = Color.White
val Elevation     = Color(0x40000000)

val DarkScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryDim,
    secondary = Secondary,
    onSecondary = Color(0xFF051E16),
    tertiary = Tertiary,
    onTertiary = Color(0xFF3A2A00),
    background = Surface,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onBackground = OnSurface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceDim,
    error = Error,
    onError = Color.White,
    outline = BorderAccent,
    inverseOnSurface = OnSurface,
    inverseSurface = SurfaceCard,
    inversePrimary = PrimaryDim,
)

// ── Typography ──
val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 34.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 11.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
)

// Aliases (legacy-friendly names sharing the same palette)
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

@Composable
fun RecapTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = AppTypography, content = content)
}
