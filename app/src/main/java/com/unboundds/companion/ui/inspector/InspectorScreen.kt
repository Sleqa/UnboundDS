package com.unboundds.companion.ui.inspector

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unboundds.companion.network.RetroArchClient
import com.unboundds.companion.network.parseReadCoreMemoryResponse
import kotlinx.coroutines.launch

/**
 * Minimal RetroArch connectivity + memory read tool. This exists to validate the
 * network path and discover/confirm Pokemon Unbound RAM addresses on real hardware
 * before any game-specific decoding is trusted.
 */
@Composable
fun InspectorScreen() {
    val client = remember { RetroArchClient() }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Not connected") }
    var addressHex by remember { mutableStateOf("02000000") }
    var length by remember { mutableStateOf("16") }
    var hexDump by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Unbound Companion", style = MaterialTheme.typography.headlineSmall)
        Text("Connects to RetroArch on 127.0.0.1:55355", style = MaterialTheme.typography.bodySmall)

        Button(
            onClick = {
                scope.launch {
                    status = when (val result = client.getVersion()) {
                        is RetroArchClient.Result.Success -> "Connected — RetroArch ${result.response}"
                        is RetroArchClient.Result.Failure -> "Error: ${result.message}"
                    }
                }
            },
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text("Test connection")
        }
        Text(status, modifier = Modifier.padding(top = 4.dp))

        Text("Memory inspector", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        Row(modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = addressHex,
                onValueChange = { addressHex = it },
                label = { Text("Address (hex)") },
                modifier = Modifier.padding(end = 8.dp),
            )
            OutlinedTextField(
                value = length,
                onValueChange = { length = it },
                label = { Text("Bytes") },
            )
        }
        Button(
            onClick = {
                scope.launch {
                    val address = addressHex.toIntOrNull(16)
                    val len = length.toIntOrNull()
                    if (address == null || len == null) {
                        hexDump = "Invalid address or length"
                        return@launch
                    }
                    hexDump = when (val result = client.readCoreMemory(address, len)) {
                        is RetroArchClient.Result.Success -> {
                            val bytes = parseReadCoreMemoryResponse(result.response)
                            bytes?.joinToString(" ") { "%02X".format(it) }
                                ?: "Read rejected by core (is a game loaded?)"
                        }
                        is RetroArchClient.Result.Failure -> "Error: ${result.message}"
                    }
                }
            },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Read memory")
        }
        Text(hexDump, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    }
}
