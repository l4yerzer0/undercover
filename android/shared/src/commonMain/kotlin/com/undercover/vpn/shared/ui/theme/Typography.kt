package com.undercover.vpn.shared.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import sing_box.shared.generated.resources.Res
import sing_box.shared.generated.resources.ibm_plex_mono
import sing_box.shared.generated.resources.ibm_plex_sans
import sing_box.shared.generated.resources.newsreader

val DossierSerif: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.newsreader, FontWeight.Normal)
    )

val DossierMono: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.ibm_plex_mono, FontWeight.Normal)
    )

val DossierSans: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.ibm_plex_sans, FontWeight.Normal)
    )

val DossierTypography: Typography
    @Composable get() = Typography(
        displayLarge = TextStyle(
            fontFamily = DossierSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-0.25).sp
        ),
        headlineLarge = TextStyle(
            fontFamily = DossierSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = DossierSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = DossierMono,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = DossierMono,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp
        ),
        labelLarge = TextStyle(
            fontFamily = DossierMono,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        labelSmall = TextStyle(
            fontFamily = DossierMono,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        )
    )
