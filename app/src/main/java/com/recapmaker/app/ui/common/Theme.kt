package com.recapmaker.app.ui.common

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Purple = Color(0xFF7C6AFF)
val PurpleDark = Color(0xFF6C5CE7)
val Emerald = Color(0xFF00E897)
val Gold = Color(0xFFFFBE2E)
val DarkBg = Color(0xFF05070E)
val CardBg = Color(0xFF12182A)
val CardBorder = Color(0xFF1E293B)
val TextPrimary = Color(0xFFE8ECF4)
val TextDim = Color(0xFF5A6280)
val TextMid = Color(0xFF8A90AD)
val ErrorRed = Color(0xFFEF4444)
val SilverColor = Color(0xFFC0C0C0)
val Rose = Color(0xFFFF4F7B)
val Cyan = Color(0xFF00E5FF)
val WarningYellow = Color(0xFFFFC107)
val SurfaceDark = Color(0xFF0A0E1A)

private val DarkScheme = darkColorScheme(
    primary = Purple, onPrimary = Color.White,
    secondary = Emerald, tertiary = Gold,
    background = DarkBg, surface = CardBg,
    surfaceVariant = CardBorder,
    onBackground = TextPrimary, onSurface = TextPrimary,
    onSurfaceVariant = TextDim, error = ErrorRed, onError = Color.White,
)

@Composable
fun RecapTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
