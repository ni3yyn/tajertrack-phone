package com.tajertrack.phone.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.tajertrack.phone.R

/**
 * Single shared rendering of the Android adaptive launcher icon.
 *
 * These are the exact same two drawable resources referenced by
 * mipmap-anydpi-v26/ic_launcher.xml:
 * - ic_launcher_background
 * - ic_launcher_foreground
 *
 * Use this composable anywhere the TajerTrack mark is displayed so the
 * launcher, splash screen, and app header can never drift into different art.
 */
@Composable
internal fun TajerTrackBrandMark(
    modifier: Modifier = Modifier,
    logoScale: Float = 1f
) {
    Box(
        modifier = modifier.graphicsLayer {
            scaleX = logoScale
            scaleY = logoScale
        }
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
    }
}
