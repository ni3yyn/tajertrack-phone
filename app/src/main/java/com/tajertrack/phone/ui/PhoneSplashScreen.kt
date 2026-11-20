package com.tajertrack.phone.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PhoneSplashScreen() {
    val logoScale = remember { Animatable(0.88f) }
    val routeProgress = remember { Animatable(0f) }
    val originProgress = remember { Animatable(0f) }
    val stationProgress = remember { Animatable(0f) }
    val destinationProgress = remember { Animatable(0f) }
    val parcelProgress = remember { Animatable(0f) }
    var brandVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch {
            logoScale.animateTo(
                targetValue = 1.035f,
                animationSpec = tween(620, easing = FastOutSlowInEasing)
            )
            logoScale.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 420f))
        }
        launch {
            routeProgress.animateTo(
                1f,
                animationSpec = tween(1_420, delayMillis = 190, easing = FastOutSlowInEasing)
            )
        }
        launch {
            originProgress.animateTo(
                1f,
                animationSpec = tween(360, delayMillis = 560, easing = FastOutSlowInEasing)
            )
        }
        launch {
            stationProgress.animateTo(
                1f,
                animationSpec = tween(330, delayMillis = 1_040, easing = FastOutSlowInEasing)
            )
        }
        launch {
            destinationProgress.animateTo(
                1f,
                animationSpec = tween(360, delayMillis = 1_460, easing = FastOutSlowInEasing)
            )
        }
        launch {
            parcelProgress.animateTo(
                1f,
                animationSpec = tween(900, delayMillis = 1_950, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(480)
            brandVisible = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TajerIvory),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Tile, route, parcel and message bubble are drawn by one Canvas
            // in a single 512x512 coordinate system. Nothing can drift outside.
            TajerTrackBrandMark(
                modifier = Modifier.size(116.dp),
                logoScale = logoScale.value,
                routeProgress = routeProgress.value,
                originProgress = originProgress.value,
                stationProgress = stationProgress.value,
                destinationProgress = destinationProgress.value,
                parcelProgress = parcelProgress.value,
                showParcel = true
            )
            Spacer(Modifier.height(24.dp))
            SplashBrandText(visible = brandVisible)
        }
    }
}

@Composable
private fun SplashBrandText(visible: Boolean) {
    Box(
        modifier = Modifier.height(94.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(480)) + slideInVertically(
                initialOffsetY = { it / 4 },
                animationSpec = tween(480, easing = FastOutSlowInEasing)
            )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "تاجر تراك",
                    color = TajerInk,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    text = "إرسال روابط التتبع من هاتفك",
                    color = TajerMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "تطبيق الهاتف المرتبط بمتجرك",
                    color = TajerMuted.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}
