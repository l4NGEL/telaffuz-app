package com.telaffuz.pronunciation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val BrandPurple = Color(0xFF6D28D9)
val BrandPurpleDark = Color(0xFF4C1D95)
val BrandPurpleLight = Color(0xFFF3E8FF)
val BrandRed = Color(0xFFDC2626)
val BackgroundSoft = Color(0xFFFAF8FF)
val SurfaceCard = Color(0xFFFFFFFF)

private val TelaffuzColorScheme = lightColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    primaryContainer = BrandPurpleLight,
    onPrimaryContainer = BrandPurpleDark,
    secondary = BrandPurple,
    background = BackgroundSoft,
    surface = SurfaceCard,
    surfaceVariant = Color(0xFFF1EEFB),
    error = BrandRed,
)

private val TelaffuzTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
)

@Composable
fun TelaffuzTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TelaffuzColorScheme,
        typography = TelaffuzTypography,
        content = content
    )
}
