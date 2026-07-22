package com.unboundds.companion.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Chunky-pixel animated "portal" fill, styled after the swirling purple O in
 * Pokemon Unbound's title logo. Quantized to a coarse cell grid and a
 * discrete palette-cycling phase (whole steps, like a GBA palette animation)
 * so it reads as pixel art rather than a smooth gradient. One narrow purple
 * hue band -- no near-black/near-white -- per feedback that a wider range
 * read as overpowering.
 */

private const val PORTAL_STEPS = 30
private const val PORTAL_CYCLE_MS = 1400

private val PortalLight = Color(0xFF7A30C0)
private val PortalDark = Color(0xFF561F96)

// Triangle wave 0->1->0 across the cycle, sampled into PORTAL_STEPS discrete colors
// once (not recomputed per-frame).
private val PortalPalette: List<Color> = List(PORTAL_STEPS) { i ->
    val half = PORTAL_STEPS / 2f
    val t = if (i < half) i / half else (PORTAL_STEPS - i) / half
    lerp(PortalLight, PortalDark, t)
}

val GoldOutline = Color(0xFFC8A028)
val GoldHighlight = Color(0xFFF0D888)

/**
 * Drives the portal phase on its own fixed-interval ticker instead of a
 * continuous Animatable -- that previous approach recomposed every consumer
 * on every display frame (e.g. 60-90x/sec) even though only PORTAL_STEPS
 * distinct visual states exist per cycle. This only triggers a recomposition
 * exactly PORTAL_STEPS times per PORTAL_CYCLE_MS.
 */
@Composable
fun portalPhase(): Int {
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val stepDelayMs = (PORTAL_CYCLE_MS / PORTAL_STEPS).toLong()
        while (true) {
            delay(stepDelayMs)
            phase = (phase + 1) % PORTAL_STEPS
        }
    }
    return phase
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
