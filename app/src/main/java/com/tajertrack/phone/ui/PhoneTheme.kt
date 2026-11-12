package com.tajertrack.phone.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TajerGreen = Color(0xFF12A66A)
private val Ink = Color(0xFF17211C)
private val Muted = Color(0xFF68756E)
private val Canvas = Color(0xFFF7F8F5)

@Composable
fun PhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = TajerGreen,
            onPrimary = Color.White,
            secondary = Ink,
            onSecondary = Color.White,
            background = Canvas,
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            surfaceVariant = Color(0xFFE9EEE9),
            onSurfaceVariant = Muted,
            error = Color(0xFFB42318)
        ),
        shapes = androidx.compose.material3.Shapes(
            extraSmall = RoundedCornerShape(0.dp),
            small = RoundedCornerShape(0.dp),
            medium = RoundedCornerShape(0.dp),
            large = RoundedCornerShape(0.dp),
            extraLarge = RoundedCornerShape(0.dp)
        ),
        content = content
    )
}
