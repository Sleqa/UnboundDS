package com.unboundds.companion.ui.dexnav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.unboundds.companion.memory.DexNavProbe
import com.unboundds.companion.network.RetroArchClient
import com.unboundds.companion.pokemon.NameTables
import kotlinx.coroutines.launch

/** Live validation view for the CFRU DexNav RAM layout. */
@Composable
fun DexNavScreen() {
    val context = LocalContext.current
    val names = remember { NameTables.load(context) }
    val client = remember { RetroArchClient() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var report by remember { mutableStateOf("Open DexNav, choose a distinctive target, then tap Read candidate.") }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("DexNav probe", style = MaterialTheme.typography.headlineSmall)
        Text(
            "CFRU-derived candidate addresses, not yet confirmed for Unbound. A valid result should show " +
                "the same species and level as the live DexNav panel. Do not treat these as app addresses " +
                "until that match is observed.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Suggested test: (1) select a distinctive species and read; (2) start its scan and read again; " +
                "(3) reach it and begin battle — Started battle should change to true.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(modifier = Modifier.padding(top = 12.dp)) {
            Button(onClick = {
                scope.launch {
                    report = "Reading CFRU candidate state…"
                    report = when (val result = DexNavProbe.read(client)) {
                        is DexNavProbe.Result.Failure -> "Read failed: ${result.message}"
                        is DexNavProbe.Result.Success -> format(result.snapshot, names)
                    }
                }
            }) { Text("Read candidate") }
            Button(
                onClick = { clipboard.setText(AnnotatedString(report)) },
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Copy report") }
        }
        SelectionContainer {
            Text(report, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

private fun format(snapshot: DexNavProbe.Snapshot, names: NameTables): String = buildString {
    appendLine("CFRU candidate probe — verification required")
    appendLine("state pointer @ 0x0203E038: 0x${snapshot.pointer.toUInt().toString(16).uppercase()}")
    appendLine("pointer is EWRAM-shaped: ${snapshot.pointerLooksValid}")
    appendLine("chain: ${snapshot.chain}; started battle: ${snapshot.startedBattle}; cooldown: ${snapshot.cooldown}")
    appendLine("last selected species: ${names.speciesName(snapshot.lastSpeciesId)} #${snapshot.lastSpeciesId}")
    val hud = snapshot.hud
    if (hud == null) {
        appendLine("No readable HUD structure. This is expected outside an active DexNav scan, or the candidate may not match Unbound.")
    } else {
        appendLine()
        appendLine("HUD species: ${names.speciesName(hud.speciesId)} #${hud.speciesId}; level ${hud.level}")
        appendLine("moves: ${hud.moves.joinToString { "${names.moveName(it)} #$it" }}")
        appendLine("item: ${names.itemName(hud.heldItemId)} #${hud.heldItemId}; ability: ${names.abilityName(hud.abilityId)} #${hud.abilityId}")
        appendLine("search level: ${hud.searchLevel}; potential: ${hud.potential}; proximity: ${hud.xProximity}, ${hud.yProximity} (total ${hud.totalProximity})")
        appendLine("environment: ${hud.environment}; target tile: ${hud.tileX}, ${hud.tileY}")
    }
    appendLine()
    append("state bytes @ 0x0203E051: ${snapshot.rawState.joinToString(" ") { "%02X".format(it) }}")
}
