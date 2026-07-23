package com.unboundds.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import com.unboundds.companion.ui.anchors.AnchorScreen
import com.unboundds.companion.ui.diff.DiffScannerScreen
import com.unboundds.companion.ui.hub.HubScreen
import com.unboundds.companion.ui.inspector.InspectorScreen
import com.unboundds.companion.ui.theme.PixelText
import com.unboundds.companion.ui.theme.RetroTheme

private enum class DevScreen { Inspector, DiffScanner, Anchors }

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
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(modifier = Modifier.padding(8.dp)) {
            Button(onClick = { screen = DevScreen.Inspector }) { Text("Inspector") }
            Button(onClick = { screen = DevScreen.DiffScanner }, modifier = Modifier.padding(start = 4.dp)) { Text("Diff") }
            Button(onClick = { screen = DevScreen.Anchors }, modifier = Modifier.padding(start = 4.dp)) { Text("Anchors") }
            Button(onClick = onClose, modifier = Modifier.padding(start = 4.dp)) { Text("Close") }
        }
        PixelText(
            "DEV TOOLS - HOLD SCREEN 3S TO OPEN",
            color = RetroTheme.text,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        when (screen) {
            DevScreen.Inspector -> InspectorScreen()
            DevScreen.DiffScanner -> DiffScannerScreen()
            DevScreen.Anchors -> AnchorScreen()
        }
    }
}
