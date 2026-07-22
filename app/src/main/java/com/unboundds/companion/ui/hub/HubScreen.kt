package com.unboundds.companion.ui.hub

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import kotlinx.coroutines.delay
import java.util.Calendar

private const val POLL_INTERVAL_MS = 1000L

// Zelda-showcase-inspired light palette for the hub.
private val HubBackground = Color(0xFFD9EDD2)   // pale mint
private val HubPanel = Color(0xFFFCFCEF)        // cream
private val HubBorderDark = Color(0xFF1A1A1A)   // near-black
private val HubBorderGold = Color(0xFFC8A028)   // gold trim
private val HubText = Color(0xFF223044)
private val SpriteCircleBg = Color(0xFFBBDCB2)  // soft green behind sprites

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
        // Main area: map panel left, status column right.
        Row(modifier = Modifier.weight(1f)) {
            // Map placeholder panel (Zelda-style framed box).
            Box(
                modifier = Modifier
                    .weight(0.68f)
                    .fillMaxHeight()
                    .background(HubBorderDark, RoundedCornerShape(6.dp))
                    .padding(3.dp)
                    .background(HubBorderGold, RoundedCornerShape(4.dp))
                    .padding(2.dp)
                    .background(HubPanel, RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center,
            ) {
                PixelText("MAP", color = Color(0xFFB0B8A8), fontSize = 16.sp)
            }

            // Right status column: clock + battery on top, party sprites below.
            Column(
                modifier = Modifier
                    .weight(0.32f)
                    .fillMaxHeight()
                    .padding(start = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelText(time, color = HubText, fontSize = 9.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    BatteryIcon(percent = battery)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 6 sprite slots, each weighted so all always fit on screen.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    repeat(6) { i ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            party.getOrNull(i)?.let { mon -> MonCircle(mon) }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom buttons, Zelda-style: black with gold border. Not wired up yet.
        Row(modifier = Modifier.fillMaxWidth().height(44.dp)) {
            HubButton("OPPONENT", modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(10.dp))
            HubButton("DEX", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HubButton(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(HubBorderDark, RoundedCornerShape(6.dp))
            .border(2.dp, HubBorderGold, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        PixelText(label, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
private fun MonCircle(mon: HubMon) {
    val context = LocalContext.current
    val sprite = remember(mon.speciesId) { SpriteAssets.frontSprite(context, mon.speciesId) }

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(SpriteCircleBg)
                .border(2.dp, HubBorderDark, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (sprite != null) {
                Image(bitmap = sprite, contentDescription = null, modifier = Modifier.size(46.dp))
            } else {
                PixelText("?", fontSize = 14.sp)
            }
        }
        // Level tag, bottom-right, overlapping the circle; outlined so it stays legible.
        OutlinedPixelText(
            text = "L${mon.level}",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 0.dp),
        )
    }
}

/** White pixel text with a dark stroke outline so it reads over any sprite/background. */
@Composable
private fun OutlinedPixelText(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = RetroTheme.pixelFont,
                fontSize = 8.sp,
                color = HubBorderDark,
                drawStyle = Stroke(width = 6f),
            ),
        )
        Text(
            text = text,
            style = TextStyle(
                fontFamily = RetroTheme.pixelFont,
                fontSize = 8.sp,
                color = Color.White,
            ),
        )
    }
}

/** Simple crisp battery glyph: body outline, fill bar by percent, small cap. */
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
            // Outline
            drawRoundRect(
                color = HubBorderDark,
                size = Size(bodyWidth, size.height),
                cornerRadius = CornerRadius(3f, 3f),
                style = Stroke(width = 3f),
            )
            // Cap
            drawRoundRect(
                color = HubBorderDark,
                topLeft = Offset(bodyWidth + 2f, size.height * 0.28f),
                size = Size(size.width - bodyWidth - 2f, size.height * 0.44f),
                cornerRadius = CornerRadius(2f, 2f),
            )
            // Fill
            val inset = 5f
            drawRect(
                color = fillColor,
                topLeft = Offset(inset, inset),
                size = Size((bodyWidth - inset * 2f) * (percent.coerceIn(0, 100) / 100f), size.height - inset * 2f),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        PixelText("$percent%", color = HubText, fontSize = 8.sp)
    }
}
