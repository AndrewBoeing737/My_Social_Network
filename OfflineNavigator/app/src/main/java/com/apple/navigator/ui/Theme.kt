package com.apple.navigator.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Apple iOS system colors
val AppleBlue = Color(0xFF0A84FF) // dark scheme blue
val AppleBlueLight = Color(0xFF007AFF)
val AppleGray = Color(0xFF8E8E93)
val AppleGrays6 = Color(0xFFF2F2F7)   // systemGray6 (light)
val AppleCardDark = Color(0xFF1C1C1E) // elevated card on dark

private val LightColors = lightColorScheme(
    primary = AppleBlueLight,
    onPrimary = Color.White,
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = AppleGrays6,
    onSurfaceVariant = AppleGray,
    scrim = Color.Black.copy(alpha = 0.35f),
)

private val DarkColors = darkColorScheme(
    primary = AppleBlue,
    onPrimary = Color.White,
    surface = AppleCardDark,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFA0A0A5),
    scrim = Color.Black.copy(alpha = 0.5f),
)

private val AppleSheetShape = RoundedCornerShape(22.dp)

val AppleShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = AppleSheetShape,
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

object AppleStyle {
    /** Translucent card background, iOS style */
    @Composable
    fun cardColor(alpha: Float = 0.94f): Color {
        val dark = isSystemInDarkTheme()
        return if (dark) AppleCardDark.copy(alpha = alpha) else Color.White.copy(alpha = alpha)
    }
}

@Composable
fun AppleTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppleShapes,
        content = content,
    )
}
