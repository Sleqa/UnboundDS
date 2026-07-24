package com.unboundds.companion.ui.opponent

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.unboundds.companion.memory.MemoryMap
import com.unboundds.companion.network.RetroArchClient
import com.unboundds.companion.pokemon.BaseStats
import com.unboundds.companion.pokemon.MoveData
import com.unboundds.companion.pokemon.NameTables
import com.unboundds.companion.pokemon.SpriteAssets
import com.unboundds.companion.ui.detail.PokemonDetailScreen
import com.unboundds.companion.ui.hub.HubMon
import com.unboundds.companion.ui.hub.OutlinedPixelText
import com.unboundds.companion.ui.hub.readPartyMons
import com.unboundds.companion.ui.theme.GoldHighlight
import com.unboundds.companion.ui.theme.GoldOutline
import com.unboundds.companion.ui.theme.PixelText
import com.unboundds.companion.ui.theme.PortalCanvas
import com.unboundds.companion.ui.theme.portalPhase
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val OpponentTextLight = Color(0xFFF0EEDA)

private const val OPPONENT_POLL_INTERVAL_MS = 10_000L
private val OpponentBackground = Color(0xFF000000)

/**
 * Opponent party screen: a 3x2 grid of the enemy party, bottom-aligned so the
 * top stays clear for future trainer info/battle context. Tapping a mon opens
 * the same detail screen used for the player's own party.
 */
@Composable
fun OpponentScreen(onClose: () -> Unit, onDataChanged: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val client = remember { RetroArchClient() }
    val map = remember { MemoryMap.load(context) }
    val names = remember { NameTables.load(context) }
    val baseStats = remember { BaseStats.load(context) }
    val moveData = remember { MoveData.load(context) }

    var opponents by remember { mutableStateOf<List<HubMon>>(emptyList()) }
    var selectedSlot by remember { mutableStateOf<Int?>(null) }
    var isStarted by remember { mutableStateOf(false) }
    val phase = portalPhase(enabled = isStarted)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(isStarted) {
        if (!isStarted) return@LaunchedEffect
        while (isActive) {
            val updatedOpponents = readPartyMons(client, map.enemyParty, baseStats)
            if (updatedOpponents != opponents) {
                opponents = updatedOpponents
                onDataChanged()
            }
            delay(OPPONENT_POLL_INTERVAL_MS)
        }
    }

    // Outer Box stays unpadded so PokemonDetailScreen (a direct sibling here,
    // same pattern as HubScreen) gets the true full screen height instead of
    // being squeezed by this screen's own content padding.
    Box(modifier = Modifier.fillMaxSize().background(OpponentBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                CloseButton(onClose)
            }

            // Top kept intentionally empty for future trainer/battle info; the
            // grid stays pinned to the bottom.
            Spacer(modifier = Modifier.weight(1f))

            repeat(2) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    repeat(3) { col ->
                        val idx = row * 3 + col
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            opponents.getOrNull(idx)?.let { mon ->
                                MonCircle(mon, phase) { selectedSlot = idx }
                            }
                        }
                    }
                }
                if (row == 0) Spacer(modifier = Modifier.height(14.dp))
            }
        }

        val detailMon = selectedSlot?.let { opponents.getOrNull(it) }
        if (detailMon != null) {
            PokemonDetailScreen(
                mon = detailMon,
                names = names,
                moveData = moveData,
                baseStats = baseStats,
                phase = phase,
                onClose = { selectedSlot = null },
            )
        }
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(2.dp, GoldOutline, RoundedCornerShape(6.dp))
            .clickable(onClick = onClose)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        OutlinedPixelText("CLOSE", fontSize = 9.sp)
    }
}

@Composable
private fun MonCircle(mon: HubMon, phase: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val sprite = remember(mon.speciesId) { SpriteAssets.frontSprite(context, mon.speciesId) }

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .shadow(elevation = 3.dp, shape = CircleShape, ambientColor = GoldHighlight, spotColor = GoldHighlight)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .border(2.dp, GoldOutline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PortalCanvas(phase = phase, modifier = Modifier.matchParentSize())
            if (sprite != null) {
                Image(
                    bitmap = sprite,
                    contentDescription = null,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(42.dp),
                )
            } else {
                PixelText("?", color = OpponentTextLight, fontSize = 14.sp)
            }
        }
        OutlinedPixelText(
            text = "L${mon.level}",
            fontSize = 8.sp,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}
