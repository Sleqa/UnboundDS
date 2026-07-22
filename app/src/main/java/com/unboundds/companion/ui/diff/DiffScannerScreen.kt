package com.unboundds.companion.ui.diff

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import com.unboundds.companion.memory.DiffScanner
import com.unboundds.companion.memory.MemoryRegion
import com.unboundds.companion.memory.MemoryRegions
import com.unboundds.companion.network.RetroArchClient
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    var selectedRegion by remember { mutableStateOf(MemoryRegions.EWRAM) }
    var status by remember { mutableStateOf("No snapshot yet") }
    var baseline by remember { mutableStateOf<ByteArray?>(null) }
    var diffs by remember { mutableStateOf<List<DiffScanner.Diff>>(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Diff scanner", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Snapshot a region, make one change in-game (e.g. spend money, take a step), " +
                "then compare to see which bytes changed.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(modifier = Modifier.padding(top = 12.dp)) {
            MemoryRegions.all.forEach { region ->
                Button(
                    onClick = {
                        selectedRegion = region
                        baseline = null
                        diffs = emptyList()
                        status = "Region set to ${region.name}"
                    },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(if (region == selectedRegion) "[${region.name}]" else region.name)
                }
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
                                diffs = scanner.diff(base, result.bytes, selectedRegion.startAddress)
                                status = "${diffs.size} changed byte-range(s) found"
                            }
                            is DiffScanner.ScanResult.Failure -> status = "Error: ${result.message}"
                        }
                    }
                },
            ) {
                Text("Compare")
            }
        }

        Text(status, modifier = Modifier.padding(top = 8.dp))

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

private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
