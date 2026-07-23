package com.unboundds.companion.ui.hub

import android.content.Context
import com.unboundds.companion.R
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unboundds.companion.memory.MemoryMap
import com.unboundds.companion.memory.PartyLayout
import com.unboundds.companion.network.RetroArchClient
import com.unboundds.companion.network.parseReadCoreMemoryResponse
import com.unboundds.companion.pokemon.BaseStats
import com.unboundds.companion.pokemon.Gen3Decrypt
import com.unboundds.companion.pokemon.MoveData
import com.unboundds.companion.pokemon.NameTables
import com.unboundds.companion.pokemon.PartyDecoder
import com.unboundds.companion.pokemon.SpriteAssets
import com.unboundds.companion.ui.detail.PokemonDetailScreen
import com.unboundds.companion.ui.theme.PixelText
import com.unboundds.companion.ui.theme.RetroTheme
import com.unboundds.companion.ui.theme.portalPhase
import kotlinx.coroutines.delay
import java.util.Calendar

private const val PARTY_POLL_INTERVAL_MS = 1000L
private const val CLOCK_BATTERY_POLL_INTERVAL_MS = 15_000L

// Light, classic-Pokemon-game palette: warm parchment background, navy ink text,
// replacing the old OLED-black Unbound theme.
private val HubBackground = Color(0xFFFBF6E9)
private val HubTextDark = Color(0xFF223057)

data class HubMon(
    val speciesId: Int,
    val nickname: String,
    val level: Int,
    val currentHp: Int,
    val maxHp: Int,
    val attack: Int,
    val defense: Int,
    val spAttack: Int,
    val spDefense: Int,
    val speed: Int,
    val heldItemId: Int,
    val abilityId: Int,
    val moves: List<Int>,
    val pp: List<Int>,
)

private suspend fun readPartyMons(
    client: RetroArchClient,
    layout: PartyLayout,
    baseStats: BaseStats,
): List<HubMon> {
    val out = mutableListOf<HubMon>()
    for (slot in 0 until layout.slotCount) {
        val addr = layout.firstSlotAddress + slot * layout.slotStride
        val result = client.readCoreMemory(addr, layout.slotStride)
        val bytes = (result as? RetroArchClient.Result.Success)
            ?.let { parseReadCoreMemoryResponse(it.response) } ?: continue
        val stats = PartyDecoder.decode(bytes) ?: continue
        if (!stats.looksValid) continue
        val decoded = Gen3Decrypt.decode(bytes) ?: continue
        out += HubMon(
            speciesId = decoded.speciesId,
            nickname = decoded.nickname,
            level = stats.level,
            currentHp = stats.currentHp,
            maxHp = stats.maxHp,
            attack = stats.attack,
            defense = stats.defense,
            spAttack = stats.spAttack,
            spDefense = stats.spDefense,
            speed = stats.speed,
            heldItemId = decoded.heldItemId,
            abilityId = baseStats.abilityIdFor(decoded.speciesId, stats.personality, decoded.hiddenAbilityFlag),
            moves = decoded.moves.toList(),
            pp = decoded.pp.toList(),
        )
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
    val names = remember { NameTables.load(context) }
    val baseStats = remember { BaseStats.load(context) }
    val moveData = remember { MoveData.load(context) }

    var party by remember { mutableStateOf<List<HubMon>>(emptyList()) }
    var battery by remember { mutableIntStateOf(batteryPercent(context)) }
    var time by remember { mutableStateOf(clockText()) }
    var selectedSlot by remember { mutableStateOf<Int?>(null) }
    // Still used by PokemonDetailScreen's own portal-swirl backdrop, which is unrelated
    // to this screen's redesign.
    val phase = portalPhase()

    // Party memory reads need to be frequent to feel live; battery/clock barely
    // change and are cheap to read but not worth waking the composition for every
    // second, so they get their own slower ticker.
    LaunchedEffect(Unit) {
        while (true) {
            party = readPartyMons(client, map.party, baseStats)
            delay(PARTY_POLL_INTERVAL_MS)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            battery = batteryPercent(context)
            time = clockText()
            delay(CLOCK_BATTERY_POLL_INTERVAL_MS)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(HubBackground)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelText("UNBOUND", color = HubTextDark, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelText(time, color = HubTextDark, fontSize = 9.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    BatteryIcon(percent = battery)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .weight(0.52f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.map_panel),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.matchParentSize(),
                    )
                    PixelText("MAP", color = HubTextDark, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    listOf(0 to 3, 1 to 4, 2 to 5).forEach { (leftIndex, rightIndex) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            listOf(leftIndex, rightIndex).forEach { idx ->
                                val mon = party.getOrNull(idx)
                                if (mon != null) {
                                    MonCircle(mon) { selectedSlot = idx }
                                } else {
                                    Spacer(modifier = Modifier.size(64.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HubButton("OPPONENT", modifier = Modifier.weight(1f))
                HubButton("DEX", modifier = Modifier.weight(1f))
            }
        }

        val detailMon = selectedSlot?.let { party.getOrNull(it) }
        if (detailMon != null) {
            PokemonDetailScreen(
                mon = detailMon,
                names = names,
                moveData = moveData,
                phase = phase,
                onClose = { selectedSlot = null },
            )
        }
    }
}

@Composable
private fun HubButton(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(2.75f)
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.button_pill),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize(),
        )
        Text(
            text = label,
            style = TextStyle(
                fontFamily = RetroTheme.pixelFont,
                fontSize = 12.sp,
                color = HubTextDark,
            ),
        )
    }
}

@Composable
private fun MonCircle(mon: HubMon, onClick: () -> Unit) {
    val context = LocalContext.current
    val sprite = remember(mon.speciesId) { SpriteAssets.frontSprite(context, mon.speciesId) }

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(elevation = 3.dp, shape = CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (sprite != null) {
                Image(bitmap = sprite, contentDescription = null, modifier = Modifier.size(48.dp))
            } else {
                PixelText("?", color = HubTextDark, fontSize = 14.sp)
            }
            Image(
                painter = painterResource(R.drawable.party_slot_frame),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
            )
        }
        OutlinedPixelText(
            text = "L${mon.level}",
            fontSize = 8.sp,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/** White pixel text with a dark stroke outline so it reads over busy art underneath. */
@Composable
fun OutlinedPixelText(
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

/** Battery glyph, dark-on-light: navy outline + cap, fill bar by charge. */
@Composable
private fun BatteryIcon(percent: Int) {
    val fillColor = when {
        percent > 50 -> Color(0xFF3C9A46)
        percent > 20 -> Color(0xFFD1A020)
        else -> Color(0xFFC0392B)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = 26.dp, height = 13.dp)) {
            val bodyWidth = size.width * 0.88f
            drawRoundRect(
                color = HubTextDark,
                size = Size(bodyWidth, size.height),
                cornerRadius = CornerRadius(3f, 3f),
                style = Stroke(width = 3f),
            )
            drawRoundRect(
                color = HubTextDark,
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
        PixelText("$percent%", color = HubTextDark, fontSize = 8.sp)
    }
}
