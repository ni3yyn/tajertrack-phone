package com.tajertrack.phone.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Plain status glyph: no circular badge or filled background.
 * Use a green check for a verified state and a red cross for a definite failure.
 */
@Composable
internal fun StatusMark(
    passed: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color? = null
) {
    Icon(
        imageVector = if (passed) Icons.Outlined.Check else Icons.Outlined.Close,
        contentDescription = if (passed) "سليم" else "غير سليم",
        tint = tint ?: if (passed) Color(0xFF287A45) else Color(0xFFB74443),
        modifier = modifier.size(size)
    )
}
