package com.unboundds.companion.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Chunky-pixel animated "portal" rendering, styled after the swirling purple
 * O in Pokemon Unbound's title logo, with a gold pixel trim. Everything is
 * quantized to a coarse cell grid so it reads as pixel art, and the animation
 * phase advances in whole steps (discrete ticks, like a GBA palette cycle)
 * rather than smoothly.
 */

// Dark-to-bright purple cycle; palette rotation is what animates the swirl.
private val PortalPalette = listOf(
    Color(0xFF160522),
    Color(0xFF2C0A48),
    Color(0xFF48177A),
    Color(0xFF6B26B0),
    Color(0xFF9542DE),
    Color(0xFFB86CF0),
    Color(0xFF9542DE),
    Color(0xFF6B26B0),
    Color(0xFF48177A),
    Color(0xFF2C0A48),
)
private val GoldLight = Color(0xFFF0D060)
private val GoldDark = Color(0xFFB58A1E)

/** Discrete animation tick, one palette step at a time. */
@Composable
fun portalPhase(): Int {
    val transition = rememberInfiniteTransition(label = "portal")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = PortalPalette.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    return phase.toInt()
}

private fun swirlColor(dx: Float, dy: Float, cell: Float, phase: Int): Color {
    val dist = sqrt(dx * dx + dy * dy)
    val angleTurns = (atan2(dy, dx) / (2 * PI) + 0.5).toFloat() // 0..1 around the circle
    val idx = floor(dist / cell * 0.55f + angleTurns * PortalPalette.size - phase)
        .toInt().mod(PortalPalette.size)
    return PortalPalette[idx]
}

/** Filled circle: gold checkered pixel ring around an animated purple swirl. */
fun DrawScope.drawPortalCircle(phase: Int, cellPx: Float) {
    val radius = size.minDimension / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val cols = ceil(size.width / cellPx).toInt()
    val rows = ceil(size.height / cellPx).toInt()
    val ringDepth = cellPx * 2f

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val x = col * cellPx + cellPx / 2f
            val y = row * cellPx + cellPx / 2f
            val dx = x - cx
            val dy = y - cy
            val dist = sqrt(dx * dx + dy * dy)
            if (dist > radius) continue

            val color = if (dist > radius - ringDepth) {
                if ((col + row) % 2 == 0) GoldLight else GoldDark
            } else {
                swirlColor(dx, dy, cellPx, phase)
            }
            drawRect(color, Offset(col * cellPx, row * cellPx), Size(cellPx, cellPx))
        }
    }
}

/**
 * Filled rectangle (for buttons): 1-cell gold checkered pixel border with
 * notched corners, animated purple swirl interior.
 */
fun DrawScope.drawPortalRect(phase: Int, cellPx: Float) {
    val cols = ceil(size.width / cellPx).toInt()
    val rows = ceil(size.height / cellPx).toInt()
    val cx = size.width / 2f
    val cy = size.height / 2f

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val edgeCol = col == 0 || col == cols - 1
            val edgeRow = row == 0 || row == rows - 1
            // Skip the very corner cells -> stepped pixel-rounded corners.
            if (edgeCol && edgeRow) continue

            val x = col * cellPx + cellPx / 2f
            val y = row * cellPx + cellPx / 2f
            val color = if (edgeCol || edgeRow) {
                if ((col + row) % 2 == 0) GoldLight else GoldDark
            } else {
                swirlColor(x - cx, y - cy, cellPx, phase)
            }
            drawRect(color, Offset(col * cellPx, row * cellPx), Size(cellPx, cellPx))
        }
    }
}
