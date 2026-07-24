package com.gbapal.companion.ui.diff

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import com.gbapal.companion.memory.DiffScanner
import com.gbapal.companion.memory.MemoryMap
import com.gbapal.companion.memory.MemoryRegion
import com.gbapal.companion.memory.MemoryRegions
import com.gbapal.companion.network.RetroArchClient
import com.gbapal.companion.network.parseReadCoreMemoryResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SAVE_BLOCK1_PTR_ADDRESS = 0x03005008
private const val SAVE_BLOCK1_SCAN_LENGTH = 0x2000 // 8KB — generous margin over vanilla FireRed's ~3.9KB struct
private const val NOISE_CALIBRATION_SAMPLES = 5

/**
 * Snapshot a memory region, perform one controlled in-game action, then
 * compare to see exactly which bytes changed. This is the primary tool for
 * discovering unknown Pokemon Unbound RAM addresses (money, party stats,
 * coordinates, etc.) rather than guessing them.
 */
@Composable
fun DiffScannerScreen() {
    val client = remember { RetroArchClient() }
    val scanner = remember { DiffScanner(client) }
    val context = LocalContext.current
    val memoryMap = remember { MemoryMap.load(context) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var selectedRegion by remember { mutableStateOf(MemoryRegions.EWRAM) }
    var status by remember { mutableStateOf("No snapshot yet") }
    var baseline by remember { mutableStateOf<ByteArray?>(null) }
    var noiseOffsets by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var diffs by remember { mutableStateOf<List<DiffScanner.Diff>>(emptyList()) }
    var hideKnown by remember { mutableStateOf(false) }

    fun knownOffsets(region: MemoryRegion): Set<Int> {
        if (!hideKnown) return emptySet()
        val ranges = buildList {
            add(memoryMap.party.firstSlotAddress to memoryMap.party.slotStride * memoryMap.party.slotCount)
            add(memoryMap.enemyParty.firstSlotAddress to memoryMap.enemyParty.slotStride * memoryMap.enemyParty.slotCount)
            add(memoryMap.overworldObjects.firstSlotAddress to memoryMap.overworldObjects.slotStride * memoryMap.overworldObjects.slotCount)
            add(memoryMap.scriptVars.firstSlotAddress to memoryMap.scriptVars.slotStride * memoryMap.scriptVars.slotCount)
            memoryMap.anchors.forEach { add(it.address to it.size) }
        }
        return ranges.flatMapTo(mutableSetOf()) { (start, length) ->
            val end = start + length
            val overlapStart = maxOf(start, region.startAddress)
            val overlapEnd = minOf(end, region.startAddress + region.length)
            if (overlapStart < overlapEnd) (overlapStart until overlapEnd).map { it - region.startAddress } else emptyList()
        }
    }

    fun resetRegion(region: MemoryRegion) {
        selectedRegion = region
        baseline = null
        noiseOffsets = emptySet()
        diffs = emptyList()
        status = "Region set to ${region.name} (0x${region.startAddress.toString(16)}, ${region.length}B)"
    }

    fun diffsAsText(): String = diffs.joinToString("\n") { d ->
        "0x${d.address.toString(16)} (${d.before.size}B): ${d.before.toHex()} -> ${d.after.toHex()}"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Diff scanner", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Snapshot a region, make one change in-game (e.g. spend money, take a step), " +
                "then compare to see which bytes changed. If EWRAM gives too many diffs, " +
                "calibrate noise first, or scope down to SaveBlock1.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "DexNav workflow: choose the small candidate window, take a snapshot, then change only one " +
                "thing: selected target, scan start, proximity, or battle. Its addresses are CFRU-derived " +
                "and require a live match before being trusted for Unbound.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )

        Row(modifier = Modifier.padding(top = 12.dp)) {
            MemoryRegions.all.forEach { region ->
                Button(
                    onClick = { resetRegion(region) },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(if (region == selectedRegion) "[${region.name}]" else region.name)
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        status = "Reading gSaveBlock1Ptr…"
                        when (val r = client.readCoreMemory(SAVE_BLOCK1_PTR_ADDRESS, 4)) {
                            is RetroArchClient.Result.Success -> {
                                val bytes = parseReadCoreMemoryResponse(r.response)
                                if (bytes == null || bytes.size < 4) {
                                    status = "Pointer read rejected — is a game loaded?"
                                } else {
                                    val ptr = (bytes[0].toInt() and 0xFF) or
                                        ((bytes[1].toInt() and 0xFF) shl 8) or
                                        ((bytes[2].toInt() and 0xFF) shl 16) or
                                        ((bytes[3].toInt() and 0xFF) shl 24)
                                    if (ptr !in 0x02000000..0x0203FFFF || ptr and 3 != 0) {
                                        status = "Pointer was 0x${ptr.toUInt().toString(16)} — not a valid EWRAM SaveBlock1 pointer. Try the direct candidate region."
                                    } else {
                                        resetRegion(MemoryRegion("SaveBlock1", ptr, SAVE_BLOCK1_SCAN_LENGTH))
                                    }
                                }
                            }
                            is RetroArchClient.Result.Failure -> status = "Error: ${r.message}"
                        }
                    }
                },
            ) {
            Text("Use SaveBlock1 pointer")
            }
        }

        Row(modifier = Modifier.padding(top = 8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        status = "Reading player overworld object…"
                        resetRegion(MemoryRegion("PlayerOW", 0x02036E38, 36))
                    }
                },
            ) {
                Text("Use player object")
            }
        }

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = hideKnown, onCheckedChange = { hideKnown = it })
            Text("Hide known anchors/party/OW ranges", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "This is opt-in: it removes addresses already mapped by the app, but leaves unknown bytes visible. " +
                "For SaveBlock1, test a change that is actually persisted (e.g. money, flags, or a save) rather than movement.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(modifier = Modifier.padding(top = 12.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        status = "Calibrating noise on ${selectedRegion.name}…"
                        val samples = mutableListOf<ByteArray>()
                        repeat(NOISE_CALIBRATION_SAMPLES) {
                            when (val sample = scanner.readRegion(selectedRegion)) {
                                is DiffScanner.ScanResult.Success -> samples += sample.bytes
                                is DiffScanner.ScanResult.Failure -> return@launch
                            }
                            delay(150)
                        }
                        if (samples.size >= 2) {
                            noiseOffsets = samples.zipWithNext()
                                .flatMapTo(mutableSetOf()) { (first, second) -> scanner.changedOffsets(first, second) }
                            status = "Noise calibrated across ${samples.size} samples: ${noiseOffsets.size} byte(s) change with no action — " +
                                "these will be filtered from future comparisons."
                        } else {
                            status = "Calibration failed — check connection"
                        }
                    }
                },
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text("Calibrate noise")
            }
        }

        Row(modifier = Modifier.padding(top = 12.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        status = "Scanning ${selectedRegion.name}…"
                        when (val result = scanner.readRegion(selectedRegion)) {
                            is DiffScanner.ScanResult.Success -> {
                                baseline = result.bytes
                                diffs = emptyList()
                                status = "Snapshot taken (${result.bytes.size} bytes). Now change something in-game."
                            }
                            is DiffScanner.ScanResult.Failure -> status = "Error: ${result.message}"
                        }
                    }
                },
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text("Take snapshot")
            }
            Button(
                onClick = {
                    val base = baseline
                    if (base == null) {
                        status = "Take a snapshot first"
                        return@Button
                    }
                    scope.launch {
                        status = "Re-scanning…"
                        when (val result = scanner.readRegion(selectedRegion)) {
                            is DiffScanner.ScanResult.Success -> {
                                diffs = scanner.diffExcludingNoise(
                                    base,
                                    result.bytes,
                                    selectedRegion.startAddress,
                                    noiseOffsets + knownOffsets(selectedRegion),
                                )
                                status = "${diffs.size} changed byte-range(s) found" +
                                    if (noiseOffsets.isNotEmpty() || hideKnown) " (filters applied)" else ""
                            }
                            is DiffScanner.ScanResult.Failure -> status = "Error: ${result.message}"
                        }
                    }
                },
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text("Compare")
            }
            Button(
                onClick = { clipboard.setText(AnnotatedString(diffsAsText())) },
            ) {
                Text("Copy results")
            }
        }

        Text(status, modifier = Modifier.padding(top = 8.dp))

        SelectionContainer {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                diffs.forEach { d ->
                    Text(
                        "0x${d.address.toString(16)} (${d.before.size}B): " +
                            "${d.before.toHex()} → ${d.after.toHex()}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
