package com.unboundds.companion.ui.anchors

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.unboundds.companion.memory.MemoryMap
import com.unboundds.companion.memory.PartyLayout
import com.unboundds.companion.network.RetroArchClient
import com.unboundds.companion.network.parseReadCoreMemoryResponse
import com.unboundds.companion.pokemon.Gen3Decrypt
import com.unboundds.companion.pokemon.Gen3Text
import com.unboundds.companion.pokemon.PartyDecoder
import kotlinx.coroutines.launch

/**
 * Reads the seeded anchor addresses live and shows decoded values, so you can
 * confirm which vanilla-FireRed anchors actually hold in Unbound. The party
 * readout decodes level/HP directly (unencrypted) plus species/nickname via
 * full Gen3 decryption — if it matches your real party, the address and the
 * decryption logic are both verified (self-checking via the struct checksum).
 */
@Composable
fun AnchorScreen() {
    val context = LocalContext.current
    val client = remember { RetroArchClient() }
    val map = remember { MemoryMap.load(context) }
    val scope = rememberCoroutineScope()

    var lines by remember { mutableStateOf(listOf("Map: Unbound ${map.unboundVersion} (${map.baseGame})")) }

    suspend fun readPartySection(label: String, layout: PartyLayout, out: MutableList<String>) {
        out += "— $label (${layout.confidence}) —"
        for (slot in 0 until layout.slotCount) {
            val addr = layout.firstSlotAddress + slot * layout.slotStride
            when (val r = client.readCoreMemory(addr, layout.slotStride)) {
                is RetroArchClient.Result.Success -> {
                    val bytes = parseReadCoreMemoryResponse(r.response)
                    if (bytes == null) {
                        out += "Slot ${slot + 1} @0x${addr.toString(16)}: read rejected"
                        continue
                    }
                    val stats = PartyDecoder.decode(bytes)
                    val decrypted = Gen3Decrypt.decode(bytes)
                    val checksumTag = if (decrypted?.checksumValid == true) "OK" else "FAIL"
                    val speciesLine = if (decrypted != null) {
                        " | species #${decrypted.speciesId} \"${decrypted.nickname}\" [checksum $checksumTag]"
                    } else {
                        ""
                    }
                    out += "Slot ${slot + 1} @0x${addr.toString(16)}: " +
                        (stats?.summary ?: "empty/invalid") + speciesLine
                }
                is RetroArchClient.Result.Failure -> out += "Slot ${slot + 1}: ${r.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Anchor verification", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Reads seeded FireRed anchors live. Matches against your real party/data " +
                "confirm which addresses hold in Unbound. Enemy party only populates during battle.",
            style = MaterialTheme.typography.bodySmall,
        )

        Button(
            onClick = {
                scope.launch {
                    val out = mutableListOf("Map: Unbound ${map.unboundVersion} (${map.baseGame})", "")
                    readPartySection("Party", map.party, out)
                    out += ""
                    readPartySection("Enemy party", map.enemyParty, out)

                    out += ""
                    out += "— Player overworld object (${map.overworldObjects.confidence}) —"
                    when (
                        val r = client.readCoreMemory(
                            map.overworldObjects.firstSlotAddress,
                            map.overworldObjects.slotStride,
                        )
                    ) {
                        is RetroArchClient.Result.Success -> {
                            val bytes = parseReadCoreMemoryResponse(r.response)
                            val hex = bytes?.joinToString(" ") { "%02X".format(it) } ?: "rejected"
                            out += "OW0 @0x${map.overworldObjects.firstSlotAddress.toString(16)}: $hex"
                        }
                        is RetroArchClient.Result.Failure -> out += "OW0: ${r.message}"
                    }

                    out += ""
                    out += "— Script vars 0x8000-0x800F (${map.scriptVars.confidence}) —"
                    for (i in 0 until map.scriptVars.slotCount) {
                        val addr = map.scriptVars.firstSlotAddress + i * map.scriptVars.slotStride
                        when (val r = client.readCoreMemory(addr, map.scriptVars.slotStride)) {
                            is RetroArchClient.Result.Success -> {
                                val bytes = parseReadCoreMemoryResponse(r.response)
                                val value = bytes?.let { (it[0].toInt() and 0xFF) or ((it[1].toInt() and 0xFF) shl 8) }
                                out += "Var 0x${(0x8000 + i).toString(16)}: ${value ?: "rejected"}"
                            }
                            is RetroArchClient.Result.Failure -> out += "Var 0x${(0x8000 + i).toString(16)}: ${r.message}"
                        }
                    }

                    out += ""
                    out += "— Anchors —"
                    for (anchor in map.anchors) {
                        when (val r = client.readCoreMemory(anchor.address, anchor.size)) {
                            is RetroArchClient.Result.Success -> {
                                val bytes = parseReadCoreMemoryResponse(r.response)
                                val hex = bytes?.joinToString(" ") { "%02X".format(it) } ?: "rejected"
                                val decoded = if (anchor.kind == "text" && bytes != null) {
                                    " \"${Gen3Text.decode(bytes)}\""
                                } else {
                                    ""
                                }
                                out += "${anchor.name} @0x${anchor.address.toString(16)} " +
                                    "[${anchor.confidence}]: $hex$decoded"
                            }
                            is RetroArchClient.Result.Failure -> {
                                out += "${anchor.name}: ${r.message}"
                            }
                        }
                    }
                    lines = out
                }
            },
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text("Read all anchors")
        }

        Column(modifier = Modifier.padding(top = 12.dp)) {
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}
