package com.tajertrack.phone.ui

import androidx.compose.ui.graphics.RectangleShape

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.tajertrack.phone.R

// TajerTrack's merchant dashboard palette.
private val Forest = Color(0xFF294B37)
private val ForestDeep = Color(0xFF1D3427)
private val Ivory = Color(0xFFF4F3ED)
private val Surface = Color(0xFFFFFEFA)
private val Sage = Color(0xFFE6ECE3)
private val Ink = Color(0xFF202A23)
private val Muted = Color(0xFF67736A)
private val Border = Color(0xFFDFE3D9)
private val Clay = Color(0xFFD57955)
private val Danger = Color(0xFFB74435)

val TajerForest = Forest
val TajerForestDeep = ForestDeep
val TajerIvory = Ivory
val TajerSurface = Surface
val TajerSage = Sage
val TajerInk = Ink
val TajerMuted = Muted
val TajerBorder = Border
val TajerClay = Clay
val TajerDanger = Danger

private val ReadexPro = FontFamily(
    Font(R.font.readex_pro, FontWeight.ExtraLight),
    Font(R.font.readex_pro, FontWeight.Light),
    Font(R.font.readex_pro, FontWeight.Normal),
    Font(R.font.readex_pro, FontWeight.Medium),
    Font(R.font.readex_pro, FontWeight.SemiBold),
    Font(R.font.readex_pro, FontWeight.Bold)
)

private val DefaultTypography = Typography()
private val TajerTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(fontFamily = ReadexPro),
    displayMedium = DefaultTypography.displayMedium.copy(fontFamily = ReadexPro),
    displaySmall = DefaultTypography.displaySmall.copy(fontFamily = ReadexPro),
    headlineLarge = DefaultTypography.headlineLarge.copy(fontFamily = ReadexPro),
    headlineMedium = DefaultTypography.headlineMedium.copy(fontFamily = ReadexPro),
    headlineSmall = DefaultTypography.headlineSmall.copy(fontFamily = ReadexPro),
    titleLarge = DefaultTypography.titleLarge.copy(fontFamily = ReadexPro),
    titleMedium = DefaultTypography.titleMedium.copy(fontFamily = ReadexPro),
    titleSmall = DefaultTypography.titleSmall.copy(fontFamily = ReadexPro),
    bodyLarge = DefaultTypography.bodyLarge.copy(fontFamily = ReadexPro),
    bodyMedium = DefaultTypography.bodyMedium.copy(fontFamily = ReadexPro),
    bodySmall = DefaultTypography.bodySmall.copy(fontFamily = ReadexPro),
    labelLarge = DefaultTypography.labelLarge.copy(fontFamily = ReadexPro),
    labelMedium = DefaultTypography.labelMedium.copy(fontFamily = ReadexPro),
    labelSmall = DefaultTypography.labelSmall.copy(fontFamily = ReadexPro)
)

@Composable
fun PhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Forest,
            onPrimary = Color.White,
            primaryContainer = Sage,
            onPrimaryContainer = ForestDeep,
            secondary = ForestDeep,
            onSecondary = Color.White,
            tertiary = Clay,
            onTertiary = Color.White,
            background = Ivory,
            onBackground = Ink,
            surface = Surface,
            onSurface = Ink,
            surfaceVariant = Sage,
            onSurfaceVariant = Muted,
            outline = Border,
            outlineVariant = Color(0xFFCBD3C7),
            error = Danger,
            onError = Color.White
        ),
        typography = TajerTypography,
        content = content
    )
}
