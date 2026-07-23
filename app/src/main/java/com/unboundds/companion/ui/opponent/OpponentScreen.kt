package com.unboundds.companion.ui.opponent

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unboundds.companion.memory.MemoryMap
import com.unboundds.companion.network.RetroArchClient
import com.unboundds.companion.pokemon.BaseStats
import com.unboundds.companion.pokemon.MoveData
import com.unboundds.companion.pokemon.NameTables
import com.unboundds.companion.ui.detail.PokemonDetailScreen
import com.unboundds.companion.ui.hub.HubMon
import com.unboundds.companion.ui.hub.MonCircle
import com.unboundds.companion.ui.hub.OutlinedPixelText
import com.unboundds.companion.ui.hub.readPartyMons
import com.unboundds.companion.ui.theme.GoldOutline
import com.unboundds.companion.ui.theme.portalPhase
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

private const val OPPONENT_POLL_INTERVAL_MS = 1000L
private val OpponentBackground = Color(0xFF000000)

/**
 * Opponent party screen: a 3x2 grid of the enemy party, bottom-aligned so the
 * top stays clear for future trainer info/battle context. Tapping a mon opens
 * the same detail screen used for the player's own party.
 */
@Composable
fun OpponentScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val client = remember { RetroArchClient() }
    val map = remember { MemoryMap.load(context) }
    val names = remember { NameTables.load(context) }
    val baseStats = remember { BaseStats.load(context) }
    val moveData = remember { MoveData.load(context) }

    var opponents by remember { mutableStateOf<List<HubMon>>(emptyList()) }
    var selectedSlot by remember { mutableStateOf<Int?>(null) }
    val phase = portalPhase()

    LaunchedEffect(Unit) {
        while (true) {
            opponents = readPartyMons(client, map.enemyParty, baseStats)
            delay(OPPONENT_POLL_INTERVAL_MS)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OpponentBackground)
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
