package com.unboundds.companion.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
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

/**
 * Per-cell geometry (distance/angle -> base palette index) for one canvas size.
 * This is the expensive part (sqrt/atan2 per cell) -- computed once when the
 * canvas is laid out, not on every phase tick.
 */
private class PortalGrid(val cols: Int, val rows: Int, val cellPx: Float, val baseIndex: IntArray)

private fun buildPortalGrid(width: Float, height: Float, cellPx: Float): PortalGrid {
    val cols = ceil(width / cellPx).toInt().coerceAtLeast(1)
    val rows = ceil(height / cellPx).toInt().coerceAtLeast(1)
    val cx = width / 2f
    val cy = height / 2f
    val base = IntArray(cols * rows)
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val dx = col * cellPx + cellPx / 2f - cx
            val dy = row * cellPx + cellPx / 2f - cy
            val dist = sqrt(dx * dx + dy * dy)
            val angleTurns = (atan2(dy, dx) / (2 * PI) + 0.5).toFloat()
            base[row * cols + col] = floor(dist / cellPx * 0.55f + angleTurns * PortalPalette.size).toInt()
        }
    }
    return PortalGrid(cols, rows, cellPx, base)
}

/** A Canvas that draws the animated swirl, caching per-cell geometry across phase ticks. */
@Composable
fun PortalCanvas(phase: Int, modifier: Modifier = Modifier, cellSize: Dp = 4.dp) {
    var grid by remember { mutableStateOf<PortalGrid?>(null) }
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    Canvas(
        modifier = modifier.onSizeChanged { s ->
            if (s.width > 0 && s.height > 0) {
                grid = buildPortalGrid(s.width.toFloat(), s.height.toFloat(), cellPx)
            }
        },
    ) {
        val g = grid ?: return@Canvas
        val paletteSize = PortalPalette.size
        for (row in 0 until g.rows) {
            for (col in 0 until g.cols) {
                val idx = (g.baseIndex[row * g.cols + col] - phase).mod(paletteSize)
                drawRect(
                    PortalPalette[idx],
                    Offset(col * g.cellPx, row * g.cellPx),
                    Size(g.cellPx, g.cellPx),
                )
            }
        }
    }
}
