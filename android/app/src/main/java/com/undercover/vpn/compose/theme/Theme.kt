package com.undercover.vpn.compose.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import androidx.compose.ui.graphics.Color

val DossierCream = Color(0xFFEDE7D9)
val DossierBackground = Color(0xFF0C0C0C)
val DossierMint = Color(0xFF8FE3B0)
val DossierInk = Color(0xFF08080A)
val DossierRed = Color(0xFFE55D5D)

private val DossierColorScheme =
    darkColorScheme(
        primary = DossierCream,
        onPrimary = DossierInk,
        secondary = DossierMint,
        onSecondary = DossierBackground,
        primaryContainer = DossierCream,
        onPrimaryContainer = DossierInk,
        background = DossierBackground,
        onBackground = DossierCream,
        surface = DossierBackground,
        onSurface = DossierCream,
        surfaceVariant = Color(0xFF1E1E1E),
        onSurfaceVariant = DossierCream,
        error = DossierRed
    )

@Composable
fun SFATheme(
    darkTheme: Boolean = true, // Force dark theme for Dossier style
    dynamicColor: Boolean = false, // Disable dynamic colors
    content: @Composable () -> Unit,
) {
    val colorScheme = DossierColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = com.undercover.vpn.shared.ui.theme.DossierTypography,
        shapes = Shapes,
        content = content,
    )
}
