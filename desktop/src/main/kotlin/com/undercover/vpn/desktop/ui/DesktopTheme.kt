package com.undercover.vpn.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.undercover.vpn.shared.ui.theme.DossierTypography

val DossierCream = Color(0xFFEDE7D9)
val DossierBackground = Color(0xFF0C0C0C)
val DossierMint = Color(0xFF8FE3B0)
val DossierInk = Color(0xFF08080A)
val DossierRed = Color(0xFFE55D5D)
val DossierSurfaceVariant = Color(0xFF1E1E1E)

val DossierColorScheme = darkColorScheme(
    primary = DossierMint,
    onPrimary = DossierInk,
    primaryContainer = DossierMint,
    onPrimaryContainer = DossierInk,
    background = DossierBackground,
    onBackground = DossierCream,
    surface = DossierBackground,
    onSurface = DossierCream,
    surfaceVariant = DossierSurfaceVariant,
    onSurfaceVariant = DossierCream,
    error = DossierRed
)

@Composable
fun UndercoverDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DossierColorScheme,
        typography = DossierTypography,
        content = content
    )
}
