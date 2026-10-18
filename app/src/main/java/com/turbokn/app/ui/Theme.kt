package com.turbokn.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Palette: ink black, snow white, ash gray.
val InkBlack = Color(0xFF0A0A0B)
val Sumi = Color(0xFF141416)
val Ash = Color(0xFF2A2A2E)
val Snow = Color(0xFFF2F2F0)
val Frost = Color(0xFFB9BDC4)
val Steel = Color(0xFF8A9099)
val BloodRed = Color(0xFF8C1D18)

private val DarkColors = darkColorScheme(
    primary = Snow,
    onPrimary = InkBlack,
    secondary = Frost,
    onSecondary = InkBlack,
    tertiary = Steel,
    background = InkBlack,
    onBackground = Snow,
    surface = Sumi,
    onSurface = Snow,
    surfaceVariant = Ash,
    onSurfaceVariant = Frost,
    outline = Steel,
    error = Color(0xFFCF6679),
)

private val LightColors = lightColorScheme(
    primary = InkBlack,
    onPrimary = Snow,
    secondary = Ash,
    background = Snow,
    onBackground = InkBlack,
    surface = Color(0xFFE8E8E6),
    onSurface = InkBlack,
    outline = Steel,
    error = BloodRed,
)

private val TurboKnTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 30.sp,
        letterSpacing = 6.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
    ),
)

@Composable
fun TurboKnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = TurboKnTypography,
        content = content,
    )
}
