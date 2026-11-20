package com.tajertrack.phone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private val MarkForestTop = Color(0xFF2D5640)
private val MarkForestBottom = Color(0xFF223F2F)
private val MarkIvory = Color(0xFFFFFEFA)
private val MarkClay = Color(0xFFD57955)

/**
 * One shared TajerTrack mark for the app header, drawer, dialogs, About page,
 * and splash screen. The tile and every foreground detail share one Canvas
 * and one coordinate system, so the route cannot drift outside the tile.
 */
@Composable
internal fun TajerTrackBrandMark(
    modifier: Modifier = Modifier,
    logoScale: Float = 1f,
    routeProgress: Float = 1f,
    originProgress: Float = 1f,
    stationProgress: Float = 1f,
    destinationProgress: Float = 1f,
    parcelProgress: Float = 0f,
    showParcel: Boolean = false
) {
    val route = remember { tajerTrackRoutePath() }
    val bubble = remember { tajerTrackBubblePath() }
    val pathMeasure = remember(route) {
        PathMeasure().apply { setPath(route, forceClosed = false) }
    }

    Canvas(
        modifier = modifier.graphicsLayer {
            scaleX = logoScale
            scaleY = logoScale
        }
    ) {
        val unit = size.minDimension / 512f
        val tileCorner = 112f * unit
        val routeAlpha = routeProgress.coerceIn(0f, 1f)
        val originAlpha = originProgress.coerceIn(0f, 1f)
        val stationsAlpha = stationProgress.coerceIn(0f, 1f)
        val destinationAlpha = destinationProgress.coerceIn(0f, 1f)

        // Draw the website's full background tile first; the entire route mark
        // is then painted inside this same 512-unit canvas.
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(MarkForestTop, MarkForestBottom),
                startY = 0f,
                endY = size.height
            ),
            size = size,
            cornerRadius = CornerRadius(tileCorner, tileCorner)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.07f),
            size = size,
            cornerRadius = CornerRadius(tileCorner, tileCorner),
            style = Stroke(width = 3f * unit)
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
            val radius = 7f * 1.45f * unit * stationsAlpha
            drawCircle(MarkForestBottom, radius, hub)
            drawCircle(MarkForestBottom, radius, outForDelivery)
        }

        if (originAlpha > 0f) {
            drawCircle(
                color = MarkClay,
                radius = 26f * 1.45f * unit * originAlpha,
                center = origin
            )
        }

        if (destinationAlpha > 0f) {
            val reveal = destinationAlpha
            drawCircle(
                color = MarkForestBottom,
                radius = 26f * 1.45f * unit * reveal,
                center = destination
            )
            drawCircle(
                color = MarkIvory,
                radius = 26f * 1.45f * unit * reveal,
                center = destination,
                style = Stroke(width = 14f * 1.45f * unit * reveal)
            )
        }

        // The small speech bubble sits in the open lower-right space of the
        // route, not outside or beside the green tile.
        if (destinationAlpha > 0f) {
            drawPath(
                path = bubble,
                color = MarkClay.copy(alpha = destinationAlpha)
            )
            drawPath(
                path = bubble,
                color = MarkIvory.copy(alpha = destinationAlpha),
                style = Stroke(width = 4.5f * unit, join = StrokeJoin.Round)
            )
            listOf(262f, 281f, 300f).forEach { dotX ->
                drawCircle(
                    color = MarkIvory.copy(alpha = destinationAlpha),
                    radius = 4f * unit,
                    center = Offset(dotX, 312f)
                )
            }
        }

        if (showParcel && parcelProgress > 0f && parcelProgress < 1f) {
            val position = pathMeasure.getPosition(pathMeasure.length * parcelProgress.coerceIn(0f, 1f))
            drawCircle(
                color = MarkClay,
                radius = 19f * 1.45f * unit,
                center = position
            )
            drawCircle(
                color = MarkForestBottom,
                radius = 19f * 1.45f * unit,
                center = position,
                style = Stroke(width = 7f * 1.45f * unit)
            )
        }

        if (showParcel && parcelProgress >= 0.96f) {
            val arrival = ((parcelProgress - 0.96f) / 0.04f).coerceIn(0f, 1f)
            drawCircle(
                color = MarkIvory,
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
                color = MarkForestBottom,
                style = Stroke(
                    width = 7f * unit * arrival,
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

private fun tajerTrackBubblePath(): Path = Path().apply {
    moveTo(256f, 285f)
    lineTo(307f, 285f)
    quadraticTo(322f, 285f, 322f, 300f)
    lineTo(322f, 325f)
    quadraticTo(322f, 340f, 307f, 340f)
    lineTo(287f, 340f)
    lineTo(270f, 354f)
    lineTo(270f, 340f)
    lineTo(256f, 340f)
    quadraticTo(241f, 340f, 241f, 325f)
    lineTo(241f, 300f)
    quadraticTo(241f, 285f, 256f, 285f)
    close()
}
