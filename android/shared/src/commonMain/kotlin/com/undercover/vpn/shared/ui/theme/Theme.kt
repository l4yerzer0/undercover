package com.undercover.vpn.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

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
    surface = DossierDarkPaper,
    onPrimary = DossierInk,
    onSecondary = DossierBackground,
    onTertiary = DossierInk,
    onBackground = DossierCream,
    onSurface = DossierCream
)

private val DossierLightColorScheme = lightColorScheme(
    primary = DossierInk,
    secondary = DossierMint,
    tertiary = DossierBackground,
    background = DossierCream,
    surface = DossierLightCream,
    onPrimary = DossierCream,
    onSecondary = DossierBackground,
    onTertiary = DossierCream,
    onBackground = DossierInk,
    onSurface = DossierInk
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
