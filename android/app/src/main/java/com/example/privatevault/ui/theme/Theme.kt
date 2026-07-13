package com.example.privatevault.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = PrivateTeal,
    secondary = PrivateCoral,
    tertiary = PrivateGold,
    background = PrivateBackground,
    surface = PrivateSurface,
    onPrimary = PrivateSurface,
    onSecondary = PrivateSurface,
    onBackground = PrivateInk,
    onSurface = PrivateInk
)

private val DarkColors = darkColorScheme(
    primary = PrivateTeal,
    secondary = PrivateCoral,
    tertiary = PrivateGold
)

@Composable
fun PrivateVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PrivateTypography,
        shapes = PrivateShapes,
        content = content
    )
}
