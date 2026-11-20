package com.tajertrack.phone.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Native Compose rendition of the animated brand mark at
 * apps/web/public/tajertrack-animated.svg in ni3yyn/dz-tracker.
 *
 * It uses the SVG's original viewBox geometry, palette, route, station markers,
 * and origin/destination nodes. Rendering natively keeps the launch animation
 * crisp and available offline without starting a WebView.
 */
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
            AnimatedTajerTrackMark(
                modifier = Modifier
                    .size(116.dp)
                    .clipToBounds(),
                logoScale = logoScale.value,
                routeProgress = routeProgress.value,
                originProgress = originProgress.value,
                stationProgress = stationProgress.value,
                destinationProgress = destinationProgress.value,
                parcelProgress = parcelProgress.value
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

@Composable
private fun AnimatedTajerTrackMark(
    modifier: Modifier,
    logoScale: Float,
    routeProgress: Float,
    originProgress: Float,
    stationProgress: Float,
    destinationProgress: Float,
    parcelProgress: Float
) {
    val route = remember { tajerTrackRoutePath() }
    val measure = remember(route) {
        PathMeasure().apply { setPath(route, forceClosed = false) }
    }

    Canvas(
        modifier = modifier.graphicsLayer {
            scaleX = logoScale
            scaleY = logoScale
        }
    ) {
        val unit = size.minDimension / 512f
        val forestTop = Color(0xFF2D5640)
        val forestBottom = Color(0xFF223F2F)
        val routeColor = Color(0xFFFFFEFA)
        val clay = Color(0xFFD57955)

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(forestTop, forestBottom),
                startY = 0f,
                endY = size.height
            ),
            size = size,
            cornerRadius = CornerRadius(112f * unit)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.07f),
            size = size,
            cornerRadius = CornerRadius(110.5f * unit),
            style = Stroke(width = 3f * unit)
        )

        if (routeProgress > 0f) {
            val revealedRoute = Path()
            measure.getSegment(
                startDistance = 0f,
                stopDistance = measure.length * routeProgress.coerceIn(0f, 1f),
                destination = revealedRoute,
                startWithMoveTo = true
            )
            drawPath(
                path = revealedRoute,
                color = routeColor,
                style = Stroke(
                    width = 28f * 1.45f * unit,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        fun mapped(x: Float, y: Float) = Offset(
            x = (x - 254f) * 1.45f + 256f,
            y = (y - 259.5f) * 1.45f + 256f
        )

        val hub = mapped(364f, 208f)
        val outForDelivery = mapped(148f, 304f)
        val origin = mapped(156f, 160f)
        val destination = mapped(344f, 352f)

        if (stationProgress > 0f) {
            val radius = 7f * 1.45f * unit * stationProgress
            drawCircle(forestBottom, radius, hub)
            drawCircle(forestBottom, radius, outForDelivery)
        }

        if (originProgress > 0f) {
            drawCircle(
                color = clay,
                radius = 26f * 1.45f * unit * originProgress,
                center = origin
            )
        }

        if (destinationProgress > 0f) {
            val reveal = destinationProgress.coerceIn(0f, 1f)
            drawCircle(
                color = forestBottom,
                radius = 26f * 1.45f * unit * reveal,
                center = destination
            )
            drawCircle(
                color = routeColor,
                radius = 26f * 1.45f * unit * reveal,
                center = destination,
                style = Stroke(width = 14f * 1.45f * unit * reveal)
            )
        }

        if (parcelProgress > 0f && parcelProgress < 1f) {
            val position = measure.getPosition(measure.length * parcelProgress)
            drawCircle(
                color = clay,
                radius = 19f * 1.45f * unit,
                center = position
            )
            drawCircle(
                color = forestBottom,
                radius = 19f * 1.45f * unit,
                center = position,
                style = Stroke(width = 7f * 1.45f * unit)
            )
        }

        if (parcelProgress >= 0.96f) {
            val arrival = ((parcelProgress - 0.96f) / 0.04f).coerceIn(0f, 1f)
            drawCircle(
                color = routeColor,
                radius = 33f * unit * arrival,
                center = destination
            )
            val check = Path().apply {
                moveTo(destination.x - 14f * unit, destination.y + 1f * unit)
                lineTo(destination.x - 3f * unit, destination.y + 12f * unit)
                lineTo(destination.x + 18f * unit, destination.y - 14f * unit)
            }
            drawPath(
                path = check,
                color = forestBottom,
                style = Stroke(
                    width = 7f * unit * arrival,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

private fun tajerTrackRoutePath(): Path {
    fun x(value: Float) = (value - 254f) * 1.45f + 256f
    fun y(value: Float) = (value - 259.5f) * 1.45f + 256f

    return Path().apply {
        moveTo(x(156f), y(160f))
        lineTo(x(316f), y(160f))
        arcTo(
            rect = Rect(x(268f), y(160f), x(364f), y(256f)),
            startAngleDegrees = -90f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false
        )
        lineTo(x(196f), y(256f))
        arcTo(
            rect = Rect(x(148f), y(256f), x(244f), y(352f)),
            startAngleDegrees = -90f,
            sweepAngleDegrees = -180f,
            forceMoveTo = false
        )
        lineTo(x(344f), y(352f))
    }
}
