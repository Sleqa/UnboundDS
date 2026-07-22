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
 * Chunky-pixel animated "portal" fill, styled after the swirling purple O in
 * Pokemon Unbound's title logo. Quantized to a coarse cell grid and a
 * discrete palette-cycling phase (whole steps, like a GBA palette animation)
 * so it reads as pixel art rather than a smooth gradient. Deliberately a
 * narrow band of one purple hue -- no near-black/near-white -- per feedback
 * that a wider range read as "overpowering".
 */

private val PortalPalette = listOf(
    Color(0xFF7A30C0),
    Color(0xFF6E2AB2),
    Color(0xFF6226A4),
    Color(0xFF561F96),
    Color(0xFF6226A4),
    Color(0xFF6E2AB2),
)

val GoldOutline = Color(0xFFC8A028)
val GoldHighlight = Color(0xFFF0D888)

@Composable
fun portalPhase(): Int {
    val transition = rememberInfiniteTransition(label = "portal")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = PortalPalette.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
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

/** Fills the whole draw area with the animated swirl -- caller clips to the desired shape. */
fun DrawScope.drawPortalFill(phase: Int, cellPx: Float) {
    val cols = ceil(size.width / cellPx).toInt()
    val rows = ceil(size.height / cellPx).toInt()
    val cx = size.width / 2f
    val cy = size.height / 2f
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val x = col * cellPx + cellPx / 2f
            val y = row * cellPx + cellPx / 2f
            drawRect(
                swirlColor(x - cx, y - cy, cellPx, phase),
                Offset(col * cellPx, row * cellPx),
                Size(cellPx, cellPx),
            )
        }
    }
}
