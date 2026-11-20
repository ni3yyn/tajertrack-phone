package com.tajertrack.phone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer

private val MarkForestTop = Color(0xFF2D5640)
private val MarkForestBottom = Color(0xFF223F2F)
private val MarkIvory = Color(0xFFFFFEFA)
private val MarkClay = Color(0xFFD57955)

/**
 * Shared native rendering of apps/web/public/icons/tajertrack-icon.svg.
 * The app launcher and the in-app mark use the same route, nodes and checkmark.
 */
@Composable
internal fun TajerTrackBrandMark(
    modifier: Modifier = Modifier,
    logoScale: Float = 1f,
    routeProgress: Float = 1f,
    originProgress: Float = 1f,
    stationProgress: Float = 1f,
    destinationProgress: Float = 1f,
    checkProgress: Float = destinationProgress
) {
    val route = remember { tajerTrackRoutePath() }
    val check = remember { tajerTrackCheckPath() }

    Canvas(
        modifier = modifier.graphicsLayer {
            scaleX = logoScale
            scaleY = logoScale
        }
    ) {
        val unit = size.minDimension / 512f
        val routeAlpha = routeProgress.coerceIn(0f, 1f)
        val originAlpha = originProgress.coerceIn(0f, 1f)
        val stationsAlpha = stationProgress.coerceIn(0f, 1f)
        val destinationAlpha = destinationProgress.coerceIn(0f, 1f)
        val checkAlpha = checkProgress.coerceIn(0f, 1f) * destinationAlpha

        // Exact SVG tile gradient and 112-unit rounded corner.
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(MarkForestTop, MarkForestBottom),
                startY = 0f,
                endY = size.height
            ),
            size = size,
            cornerRadius = CornerRadius(112f * unit, 112f * unit)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.12f),
            topLeft = Offset(2f * unit, 2f * unit),
            size = Size(size.width - 4f * unit, size.height - 4f * unit),
            cornerRadius = CornerRadius(110f * unit, 110f * unit),
            style = Stroke(width = 4f * unit)
        )

        drawPath(
            path = route,
            color = MarkIvory,
            alpha = routeAlpha,
            style = Stroke(
                width = 28f * 1.45f * unit,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        val hub = markPoint(364f, 208f)
        val outForDelivery = markPoint(148f, 304f)
        val origin = markPoint(156f, 160f)
        val destination = markPoint(344f, 352f)

        if (stationsAlpha > 0f) {
            val radius = 9f * 1.45f * unit * stationsAlpha
            drawCircle(MarkClay, radius, hub)
            drawCircle(MarkClay, radius, outForDelivery)
        }

        if (originAlpha > 0f) {
            drawCircle(
                color = MarkClay,
                radius = 26f * 1.45f * unit * originAlpha,
                center = origin
            )
        }

        if (destinationAlpha > 0f) {
            drawCircle(
                color = MarkIvory,
                radius = 26f * 1.45f * unit * destinationAlpha,
                center = destination
            )
        }

        if (checkAlpha > 0f) {
            drawPath(
                path = check,
                color = MarkForestBottom,
                alpha = checkAlpha,
                style = Stroke(
                    width = 6f * 1.45f * unit,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

/** SVG art transform: translate(256 256) scale(1.45) translate(-254 -259.5). */
private fun markPoint(x: Float, y: Float) = Offset(
    x = (x - 254f) * 1.45f + 256f,
    y = (y - 259.5f) * 1.45f + 256f
)

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

private fun tajerTrackCheckPath(): Path {
    fun x(value: Float) = (value - 254f) * 1.45f + 256f
    fun y(value: Float) = (value - 259.5f) * 1.45f + 256f

    return Path().apply {
        moveTo(x(334f), y(353f))
        lineTo(x(341f), y(360f))
        lineTo(x(354f), y(346f))
    }
}
