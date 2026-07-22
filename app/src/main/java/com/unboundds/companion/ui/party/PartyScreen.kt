package com.unboundds.companion.ui.party

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unboundds.companion.memory.MemoryMap
import com.unboundds.companion.memory.PartyLayout
import com.unboundds.companion.network.RetroArchClient
import com.unboundds.companion.network.parseReadCoreMemoryResponse
import com.unboundds.companion.pokemon.Gen3Decrypt
import com.unboundds.companion.pokemon.NameTables
import com.unboundds.companion.pokemon.PartyDecoder
import com.unboundds.companion.pokemon.SpriteAssets
import com.unboundds.companion.ui.theme.PixelHpBar
import com.unboundds.companion.ui.theme.PixelText
import com.unboundds.companion.ui.theme.RetroPanel
import com.unboundds.companion.ui.theme.RetroTheme
import kotlinx.coroutines.delay

private const val POLL_INTERVAL_MS = 1000L

data class PartyMonUi(
    val slot: Int,
    val speciesId: Int,
    val speciesName: String,
    val nickname: String,
    val level: Int,
    val currentHp: Int,
    val maxHp: Int,
)

private suspend fun readParty(client: RetroArchClient, layout: PartyLayout, names: NameTables): List<PartyMonUi> {
    val out = mutableListOf<PartyMonUi>()
    for (slot in 0 until layout.slotCount) {
        val addr = layout.firstSlotAddress + slot * layout.slotStride
        val result = client.readCoreMemory(addr, layout.slotStride)
        val bytes = (result as? RetroArchClient.Result.Success)?.let { parseReadCoreMemoryResponse(it.response) } ?: continue
        val stats = PartyDecoder.decode(bytes) ?: continue
        if (!stats.looksValid) continue
        val decoded = Gen3Decrypt.decode(bytes) ?: continue
        out += PartyMonUi(
            slot = slot,
            speciesId = decoded.speciesId,
            speciesName = names.speciesName(decoded.speciesId),
            nickname = decoded.nickname,
            level = stats.level,
            currentHp = stats.currentHp,
            maxHp = stats.maxHp,
        )
    }
    return out
}

@Composable
fun PartyScreen() {
    val context = LocalContext.current
    val client = remember { RetroArchClient() }
    val map = remember { MemoryMap.load(context) }
    val names = remember { NameTables.load(context) }

    var party by remember { mutableStateOf<List<PartyMonUi>>(emptyList()) }
    var enemy by remember { mutableStateOf<List<PartyMonUi>>(emptyList()) }
    var status by remember { mutableStateOf("CONNECTING...") }

    LaunchedEffect(Unit) {
        while (true) {
            val newParty = readParty(client, map.party, names)
            val newEnemy = readParty(client, map.enemyParty, names)
            party = newParty
            enemy = newEnemy
            status = if (newParty.isEmpty()) "NO DATA - IS UNBOUND RUNNING?" else ""
            delay(POLL_INTERVAL_MS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RetroTheme.background)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        PixelText("PARTY", color = RetroTheme.textOnDark, fontSize = 14.sp)
        if (status.isNotEmpty()) {
            PixelText(status, color = RetroTheme.textOnDark, fontSize = 8.sp, modifier = Modifier.padding(top = 8.dp))
        }
        party.forEach { mon -> MonPanel(mon) }

        if (enemy.isNotEmpty()) {
            PixelText("ENEMY", color = RetroTheme.accent, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            enemy.forEach { mon -> MonPanel(mon) }
        }
    }
}

@Composable
private fun MonPanel(mon: PartyMonUi) {
    val context = LocalContext.current
    val sprite = remember(mon.speciesId) { SpriteAssets.frontSprite(context, mon.speciesId) }

    RetroPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                if (sprite != null) {
                    Image(bitmap = sprite, contentDescription = mon.speciesName, modifier = Modifier.size(56.dp))
                } else {
                    PixelText("?", fontSize = 20.sp)
                }
            }
            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                PixelText(mon.nickname.uppercase(), fontSize = 10.sp)
                PixelText("${mon.speciesName.uppercase()}  L${mon.level}", fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp))
                PixelHpBar(
                    fraction = if (mon.maxHp > 0) mon.currentHp.toFloat() / mon.maxHp else 0f,
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                )
                PixelText("HP ${mon.currentHp}/${mon.maxHp}", fontSize = 8.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}
