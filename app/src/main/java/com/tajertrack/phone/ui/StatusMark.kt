package com.tajertrack.phone.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun StatusMark(
    passed: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    Icon(
        imageVector = if (passed) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
        contentDescription = if (passed) "سليم" else "غير سليم",
        tint = if (passed) Color(0xFF287A45) else Color(0xFFB74443),
        modifier = modifier.size(size)
    )
}
