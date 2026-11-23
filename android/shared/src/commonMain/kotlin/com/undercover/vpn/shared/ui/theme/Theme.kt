package com.undercover.vpn.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.Color

val DossierShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

private val DossierDarkColorScheme = darkColorScheme(
    primary = DossierCream,
    secondary = DossierMint,
    tertiary = DossierLightCream,
    background = DossierBackground,
    surface = DossierBackground,
    surfaceVariant = Color(0xFF161618),
    surfaceTint = Color.Transparent,
    surfaceDim = Color(0xFF08080A),
    surfaceBright = Color(0xFF1E1E22),
    surfaceContainerLowest = Color(0xFF08080A),
    surfaceContainerLow = Color(0xFF0E0E10),
    surfaceContainer = Color(0xFF141416),
    surfaceContainerHigh = Color(0xFF1A1A1E),
    surfaceContainerHighest = Color(0xFF222226),
    onPrimary = DossierInk,
    onSecondary = DossierBackground,
    onTertiary = DossierInk,
    onBackground = DossierCream,
    onSurface = DossierCream,
    onSurfaceVariant = DossierCream.copy(alpha = 0.7f),
    outline = DossierCream.copy(alpha = 0.25f),
    outlineVariant = DossierCream.copy(alpha = 0.15f),
    error = DossierRed,
    onError = DossierInk,
    errorContainer = Color(0xFF3B1515),
    onErrorContainer = DossierRed,
)

private val DossierLightColorScheme = lightColorScheme(
    primary = DossierInk,
    secondary = DossierMint,
    tertiary = DossierBackground,
    background = DossierCream,
    surface = DossierCream,
    surfaceVariant = Color(0xFFE2DCD0),
    surfaceTint = Color.Transparent,
    surfaceDim = Color(0xFFE0DACB),
    surfaceBright = Color(0xFFFAF7F0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F0E6),
    surfaceContainer = Color(0xFFEBE6DC),
    surfaceContainerHigh = Color(0xFFE5E0D6),
    surfaceContainerHighest = Color(0xFFDFDAD0),
    onPrimary = DossierCream,
    onSecondary = DossierBackground,
    onTertiary = DossierCream,
    onBackground = DossierInk,
    onSurface = DossierInk,
    onSurfaceVariant = DossierInk.copy(alpha = 0.7f),
    outline = DossierInk.copy(alpha = 0.25f),
    outlineVariant = DossierInk.copy(alpha = 0.15f),
    error = Color(0xFFBA1A1A),
    onError = DossierCream,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun DossierTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DossierDarkColorScheme
    } else {
        DossierLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DossierTypography,
        shapes = DossierShapes,
        content = content
    )
}
