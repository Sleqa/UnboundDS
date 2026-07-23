package com.unboundds.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.unboundds.companion.ui.anchors.AnchorScreen
import com.unboundds.companion.ui.diff.DiffScannerScreen
import com.unboundds.companion.ui.dexnav.DexNavScreen
import com.unboundds.companion.ui.hub.HubScreen
import com.unboundds.companion.ui.inspector.InspectorScreen
import com.unboundds.companion.ui.theme.PixelText
import com.unboundds.companion.ui.theme.RetroPanel
import com.unboundds.companion.ui.theme.RetroTheme

private enum class DevScreen { Inspector, DiffScanner, Anchors, DexNav }

class MainActivity : ComponentActivity() {

    // Toggled by a deliberate three-second touch-and-hold on the companion screen.
    private val showDevTools: MutableState<Boolean> = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showDevTools.value) {
                        DevTools(onClose = { showDevTools.value = false })
                    } else {
                        HubScreen(onDevToolsRequested = { showDevTools.value = true })
                    }
                }
            }
        }
    }

}

@androidx.compose.runtime.Composable
private fun DevTools(onClose: () -> Unit) {
    var screen by remember { mutableStateOf(DevScreen.Inspector) }
    Column(modifier = Modifier.fillMaxSize().background(RetroTheme.background)) {
        RetroPanel(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column {
                PixelText("UNBOUNDDS // DEVELOPER TOOLS", color = RetroTheme.text)
                PixelText(
                    "Hold the companion screen for 3 seconds to open. Read-only tools only.",
                    color = RetroTheme.text,
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 8.sp,
                )
                Row(modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
                    DevTab("Inspector", screen == DevScreen.Inspector) { screen = DevScreen.Inspector }
                    DevTab("Diff", screen == DevScreen.DiffScanner) { screen = DevScreen.DiffScanner }
                    DevTab("Anchors", screen == DevScreen.Anchors) { screen = DevScreen.Anchors }
                    DevTab("DexNav", screen == DevScreen.DexNav) { screen = DevScreen.DexNav }
                    Button(onClick = onClose, modifier = Modifier.padding(start = 4.dp)) { Text("Close") }
                }
            }
        }
        Surface(modifier = Modifier.fillMaxWidth(), color = Color.White) {
            when (screen) {
                DevScreen.Inspector -> InspectorScreen()
                DevScreen.DiffScanner -> DiffScannerScreen()
                DevScreen.Anchors -> AnchorScreen()
                DevScreen.DexNav -> DexNavScreen()
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun DevTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.padding(end = 4.dp)) {
        Text(if (selected) "[$label]" else label)
    }
}
