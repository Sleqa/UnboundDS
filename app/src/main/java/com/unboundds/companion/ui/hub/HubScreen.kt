package com.unboundds.companion.ui.hub

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unboundds.companion.memory.MemoryMap
import com.unboundds.companion.memory.PartyLayout
import com.unboundds.companion.network.RetroArchClient
import com.unboundds.companion.network.parseReadCoreMemoryResponse
import com.unboundds.companion.pokemon.Gen3Decrypt
import com.unboundds.companion.pokemon.PartyDecoder
import com.unboundds.companion.pokemon.SpriteAssets
import com.unboundds.companion.ui.theme.PixelText
import com.unboundds.companion.ui.theme.RetroTheme
import com.unboundds.companion.ui.theme.drawPortalCircle
import com.unboundds.companion.ui.theme.drawPortalRect
import com.unboundds.companion.ui.theme.portalPhase
import kotlinx.coroutines.delay
import java.util.Calendar

private const val POLL_INTERVAL_MS = 1000L

// OLED-black theme, Unbound-title-screen flavored.
private val HubBackground = Color(0xFF000000)
private val HubPanel = Color(0xFFFCFCEF)        // map interior stays cream for now
private val HubGold = Color(0xFFC8A028)
private val HubTextLight = Color(0xFFF0EEDA)

data class HubMon(val speciesId: Int, val level: Int)

private suspend fun readPartyMons(client: RetroArchClient, layout: PartyLayout): List<HubMon> {
    val out = mutableListOf<HubMon>()
    for (slot in 0 until layout.slotCount) {
        val addr = layout.firstSlotAddress + slot * layout.slotStride
        val result = client.readCoreMemory(addr, layout.slotStride)
        val bytes = (result as? RetroArchClient.Result.Success)
            ?.let { parseReadCoreMemoryResponse(it.response) } ?: continue
        val stats = PartyDecoder.decode(bytes) ?: continue
        if (!stats.looksValid) continue
        val decoded = Gen3Decrypt.decode(bytes) ?: continue
        out += HubMon(speciesId = decoded.speciesId, level = stats.level)
    }
    return out
}

private fun batteryPercent(context: Context): Int {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
}

private fun clockText(): String {
    val cal = Calendar.getInstance()
    val h = cal.get(Calendar.HOUR)
    val hour12 = if (h == 0) 12 else h
    val minute = cal.get(Calendar.MINUTE)
    val ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    return "%d:%02d %s".format(hour12, minute, ampm)
}

@Composable
fun HubScreen() {
    val context = LocalContext.current
    val client = remember { RetroArchClient() }
    val map = remember { MemoryMap.load(context) }

    var party by remember { mutableStateOf<List<HubMon>>(emptyList()) }
    var battery by remember { mutableIntStateOf(batteryPercent(context)) }
    var time by remember { mutableStateOf(clockText()) }
    val phase = portalPhase()

    LaunchedEffect(Unit) {
        while (true) {
            party = readPartyMons(client, map.party)
            battery = batteryPercent(context)
            time = clockText()
            delay(POLL_INTERVAL_MS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HubBackground)
            .padding(10.dp),
    ) {
        // Full-width top bar. Left side is intentionally empty for future
        // widgets above the map; clock + battery sit on the right.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelText(time, color = HubTextLight, fontSize = 9.sp)
            Spacer(modifier = Modifier.width(8.dp))
            BatteryIcon(percent = battery)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main area: map panel and sprite column share the same top edge.
        Row(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .weight(0.68f)
                    .fillMaxHeight()
                    .background(HubGold, RoundedCornerShape(4.dp))
                    .padding(2.dp)
                    .background(HubPanel, RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center,
            ) {
                PixelText("MAP", color = Color(0xFFB0B8A8), fontSize = 16.sp)
            }

            Column(
                modifier = Modifier
                    .weight(0.32f)
                    .fillMaxHeight()
                    .padding(start = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(6) { i ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        party.getOrNull(i)?.let { mon -> MonCircle(mon, phase) }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom buttons: gold pixel border, animated portal interior. Not wired up yet.
        Row(modifier = Modifier.fillMaxWidth().height(44.dp)) {
            HubButton("OPPONENT", phase, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(10.dp))
            HubButton("DEX", phase, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HubButton(label: String, phase: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawPortalRect(phase, cellPx = 4.dp.toPx())
        }
        OutlinedPixelText(label, fontSize = 11.sp)
    }
}

@Composable
private fun MonCircle(mon: HubMon, phase: Int) {
    val context = LocalContext.current
    val sprite = remember(mon.speciesId) { SpriteAssets.frontSprite(context, mon.speciesId) }

    Box(contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawPortalCircle(phase, cellPx = 4.dp.toPx())
            }
            if (sprite != null) {
                Image(bitmap = sprite, contentDescription = null, modifier = Modifier.size(44.dp))
            } else {
                PixelText("?", color = HubTextLight, fontSize = 14.sp)
            }
        }
        OutlinedPixelText(
            text = "L${mon.level}",
            fontSize = 8.sp,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/** White pixel text with a dark stroke outline so it reads over the portal art. */
@Composable
private fun OutlinedPixelText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = RetroTheme.pixelFont,
                fontSize = fontSize,
                color = Color(0xFF14041E),
                drawStyle = Stroke(width = 6f),
            ),
        )
        Text(
            text = text,
            style = TextStyle(
                fontFamily = RetroTheme.pixelFont,
                fontSize = fontSize,
                color = Color.White,
            ),
        )
    }
}

/** Battery glyph, light-on-black: outline + cap in gold, fill bar by charge. */
@Composable
private fun BatteryIcon(percent: Int) {
    val fillColor = when {
        percent > 50 -> Color(0xFF58C858)
        percent > 20 -> Color(0xFFE8B820)
        else -> Color(0xFFD84040)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = 26.dp, height = 13.dp)) {
            val bodyWidth = size.width * 0.88f
            drawRoundRect(
                color = HubGold,
                size = Size(bodyWidth, size.height),
                cornerRadius = CornerRadius(3f, 3f),
                style = Stroke(width = 3f),
            )
            drawRoundRect(
                color = HubGold,
                topLeft = Offset(bodyWidth + 2f, size.height * 0.28f),
                size = Size(size.width - bodyWidth - 2f, size.height * 0.44f),
                cornerRadius = CornerRadius(2f, 2f),
            )
            val inset = 5f
            drawRect(
                color = fillColor,
                topLeft = Offset(inset, inset),
                size = Size(
                    (bodyWidth - inset * 2f) * (percent.coerceIn(0, 100) / 100f),
                    size.height - inset * 2f,
                ),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        PixelText("$percent%", color = HubTextLight, fontSize = 8.sp)
    }
}
