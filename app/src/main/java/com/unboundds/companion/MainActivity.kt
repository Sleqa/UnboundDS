package com.unboundds.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unboundds.companion.ui.anchors.AnchorScreen
import com.unboundds.companion.ui.diff.DiffScannerScreen
import com.unboundds.companion.ui.inspector.InspectorScreen
import com.unboundds.companion.ui.party.PartyScreen

private enum class Screen { Party, Inspector, DiffScanner, Anchors }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var screen by remember { mutableStateOf(Screen.Party) }
                    Column {
                        Row(modifier = Modifier.padding(8.dp)) {
                            Button(onClick = { screen = Screen.Party }) { Text("Party") }
                            Button(onClick = { screen = Screen.Inspector }) { Text("Inspector") }
                            Button(onClick = { screen = Screen.DiffScanner }) { Text("Diff") }
                            Button(onClick = { screen = Screen.Anchors }) { Text("Anchors") }
                        }
                        when (screen) {
                            Screen.Party -> PartyScreen()
                            Screen.Inspector -> InspectorScreen()
                            Screen.DiffScanner -> DiffScannerScreen()
                            Screen.Anchors -> AnchorScreen()
                        }
                    }
                }
            }
        }
    }
}
