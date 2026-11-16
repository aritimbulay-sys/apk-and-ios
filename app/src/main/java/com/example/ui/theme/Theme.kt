package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = CyanBright,
    onPrimary = NavyBackgroundDark,
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = Color(0xFFB2F5FF),
    secondary = AmberAccent,
    onSecondary = NavyBackgroundDark,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = AmberContainerLight,
    tertiary = EmeraldOnline,
    error = RoseAlert,
    background = NavyBackgroundDark,
    onBackground = TextPrimaryDark,
    surface = NavySurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = NavyVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = NavyCardDark,
    surfaceContainer = NavyCardDark,
    surfaceContainerHigh = NavyVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimary,
    onPrimary = Color.White,
    primaryContainer = CyanContainerLight,
    onPrimaryContainer = Color(0xFF003640),
    secondary = AmberAccent,
    onSecondary = NavyBackgroundDark,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = Color(0xFF3E2700),
    tertiary = EmeraldOnline,
    error = RoseAlert,
    background = SlateBackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SlateSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SlateVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = SlateCardLight,
    surfaceContainer = SlateCardLight,
    surfaceContainerHigh = SlateVariantLight
)

@Composable
fun HotsMonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HotsMonTypography,
        content = content
    )
}
