package com.unboundds.companion.ui.party

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.unboundds.companion.memory.MemoryMap
import com.unboundds.companion.memory.PartyLayout
import com.unboundds.companion.network.RetroArchClient
import com.unboundds.companion.network.parseReadCoreMemoryResponse
import com.unboundds.companion.pokemon.Gen3Decrypt
import com.unboundds.companion.pokemon.NameTables
import com.unboundds.companion.pokemon.PartyDecoder
import com.unboundds.companion.pokemon.SpriteAssets
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
    var status by remember { mutableStateOf("Connecting…") }

    LaunchedEffect(Unit) {
        while (true) {
            val newParty = readParty(client, map.party, names)
            val newEnemy = readParty(client, map.enemyParty, names)
            party = newParty
            enemy = newEnemy
            status = if (newParty.isEmpty()) "No party data — is RetroArch running with Unbound loaded?" else ""
            delay(POLL_INTERVAL_MS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Party", style = MaterialTheme.typography.headlineSmall)
        if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
        party.forEach { mon -> MonRow(mon) }

        if (enemy.isNotEmpty()) {
            Text("Enemy", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
            enemy.forEach { mon -> MonRow(mon) }
        }
    }
}

@Composable
private fun MonRow(mon: PartyMonUi) {
    val context = LocalContext.current
    val sprite = remember(mon.speciesId) { SpriteAssets.frontSprite(context, mon.speciesId) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (sprite != null) {
                    Image(bitmap = sprite, contentDescription = mon.speciesName, modifier = Modifier.size(48.dp))
                } else {
                    Text("?", style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text("${mon.nickname}  (${mon.speciesName})  Lv${mon.level}", style = MaterialTheme.typography.bodyMedium)
                val hpFraction = if (mon.maxHp > 0) mon.currentHp.toFloat() / mon.maxHp else 0f
                val hpColor = when {
                    hpFraction > 0.5f -> Color(0xFF4CAF50)
                    hpFraction > 0.2f -> Color(0xFFFFC107)
                    else -> Color(0xFFF44336)
                }
                LinearProgressIndicator(
                    progress = { hpFraction.coerceIn(0f, 1f) },
                    color = hpColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
                Text("${mon.currentHp}/${mon.maxHp} HP", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
