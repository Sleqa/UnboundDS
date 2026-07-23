package com.unboundds.companion.ui.hub

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.BatteryManager
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.unboundds.companion.ui.theme.PixelHpBar
import com.unboundds.companion.ui.theme.PixelPanel
import com.unboundds.companion.ui.theme.PixelText
import com.unboundds.companion.ui.theme.PokedexShell
import com.unboundds.companion.ui.theme.RetroTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Calendar

/**
 * The current public build deliberately favours battery life. Battle-aware polling
 * can use a shorter interval later once the battle-state memory anchor is confirmed.
 */
private enum class PollingMode(val intervalMs: Long) {
    LowPower(10_000L),
}

private val activePollingMode = PollingMode.LowPower
private const val CLOCK_BATTERY_POLL_INTERVAL_MS = 15_000L
private const val IDLE_DIM_DELAY_MS = 10_000L
private const val IDLE_DIM_BRIGHTNESS = 0.08f

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
    val type1: String?,
    val type2: String?,
    val moves: List<Int>,
    val pp: List<Int>,
)

private suspend fun readPartyMons(
    client: RetroArchClient,
    layout: PartyLayout,
    baseStats: BaseStats,
): List<HubMon> {
    // Party slots are contiguous: one 600-byte request replaces six UDP sockets,
    // sends, receives, and closes per refresh.
    val totalLength = layout.slotStride * layout.slotCount
    val response = client.readCoreMemory(layout.firstSlotAddress, totalLength)
    val partyBytes = (response as? RetroArchClient.Result.Success)
        ?.let { parseReadCoreMemoryResponse(it.response) }
        ?.takeIf { it.size >= totalLength }
        ?: return emptyList()

    val out = mutableListOf<HubMon>()
    for (slot in 0 until layout.slotCount) {
        val offset = slot * layout.slotStride
        val bytes = partyBytes.copyOfRange(offset, offset + layout.slotStride)
        val stats = PartyDecoder.decode(bytes) ?: continue
        if (!stats.looksValid) continue
        val decoded = Gen3Decrypt.decode(bytes) ?: continue
        val base = baseStats.entry(decoded.speciesId)
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
            type1 = base?.type1,
            type2 = base?.type2,
            moves = decoded.moves.toList(),
            pp = decoded.pp.toList(),
        )
    }
    return out
}

private fun batteryPercent(context: Context): Int {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun clockText(): String {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    val minute = calendar.get(Calendar.MINUTE)
    val amPm = if (calendar.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    return "%d:%02d %s".format(hour, minute, amPm)
}

@Composable
fun HubScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }
    val client = remember { RetroArchClient() }
    val memoryMap = remember { MemoryMap.load(context) }
    val names = remember { NameTables.load(context) }
    val baseStats = remember { BaseStats.load(context) }
    val moveData = remember { MoveData.load(context) }

    var party by remember { mutableStateOf<List<HubMon>>(emptyList()) }
    var battery by remember { mutableIntStateOf(batteryPercent(context)) }
    var time by remember { mutableStateOf(clockText()) }
    var selectedSlot by remember { mutableStateOf<Int?>(null) }
    var isStarted by remember { mutableStateOf(false) }
    var dimmed by remember { mutableStateOf(false) }
    var lastActivityMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // An overlay only darkens pixels; this reduces the actual display/backlight
    // brightness too, which is the meaningful saving on LCD handhelds.
    DisposableEffect(dimmed, activity) {
        if (dimmed && activity != null) {
            val window = activity.window
            val previousBrightness = window.attributes.screenBrightness
            window.attributes = window.attributes.apply { screenBrightness = IDLE_DIM_BRIGHTNESS }
            onDispose {
                window.attributes = window.attributes.apply { screenBrightness = previousBrightness }
            }
        } else {
            onDispose { }
        }
    }

    LaunchedEffect(isStarted) {
        if (!isStarted) return@LaunchedEffect
        while (isActive) {
            val updatedParty = readPartyMons(client, memoryMap.party, baseStats)
            if (updatedParty != party) {
                party = updatedParty
                dimmed = false
                lastActivityMs = SystemClock.elapsedRealtime()
            }
            delay(activePollingMode.intervalMs)
        }
    }
    LaunchedEffect(isStarted) {
        if (!isStarted) return@LaunchedEffect
        while (isActive) {
            battery = batteryPercent(context)
            time = clockText()
            delay(CLOCK_BATTERY_POLL_INTERVAL_MS)
        }
    }
    LaunchedEffect(lastActivityMs) {
        val scheduledActivityMs = lastActivityMs
        delay(IDLE_DIM_DELAY_MS)
        if (lastActivityMs == scheduledActivityMs) dimmed = true
    }

    PokedexShell {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(9.dp),
        ) {
            TopStatusBar(time = time, battery = battery)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.weight(1f)) {
                FieldScanner(modifier = Modifier.weight(0.55f).fillMaxHeight())
                Spacer(modifier = Modifier.width(8.dp))
                PartyRack(
                    party = party,
                    onSelect = { selectedSlot = it },
                    modifier = Modifier.weight(0.45f).fillMaxHeight(),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().height(41.dp)) {
                FooterModule("OPPONENT", RetroTheme.red, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                FooterModule("DEX", RetroTheme.yellow, modifier = Modifier.weight(1f))
            }
        }

        selectedSlot?.let { index ->
            party.getOrNull(index)?.let { mon ->
                PokemonDetailScreen(
                    mon = mon,
                    names = names,
                    moveData = moveData,
                    onClose = { selectedSlot = null },
                )
            }
        }

        if (dimmed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RetroTheme.ink.copy(alpha = 0.72f))
                    .clickable {
                        dimmed = false
                        lastActivityMs = SystemClock.elapsedRealtime()
                    },
            )
        }
    }
}

@Composable
private fun TopStatusBar(time: String, battery: Int) {
    PixelPanel(accent = RetroTheme.red, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(RetroTheme.red, CircleShape)
                    .border(2.dp, RetroTheme.paper, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(7.dp).background(RetroTheme.paper, CircleShape))
            }
            Spacer(modifier = Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                PixelText("UNBOUND", color = RetroTheme.paper, fontSize = 11.sp)
                PixelText("COMPANION // LINK", color = RetroTheme.muted, fontSize = 6.sp)
            }
            PixelText(time, color = RetroTheme.paper, fontSize = 7.sp)
            Spacer(modifier = Modifier.width(7.dp))
            BatteryIcon(percent = battery)
        }
    }
}

@Composable
private fun FieldScanner(modifier: Modifier = Modifier) {
    PixelPanel(modifier = modifier, accent = RetroTheme.teal) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val grid = 14.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(RetroTheme.screenDark.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), 1f)
                    x += grid
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(RetroTheme.screenDark.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), 1f)
                    y += grid
                }
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = minOf(size.width, size.height) * 0.38f
                drawCircle(RetroTheme.screenDark.copy(alpha = 0.6f), maxRadius, center, style = Stroke(2f))
                drawCircle(RetroTheme.screenDark.copy(alpha = 0.6f), maxRadius * 0.5f, center, style = Stroke(2f))
                drawLine(RetroTheme.screenDark.copy(alpha = 0.7f), Offset(center.x, 0f), Offset(center.x, size.height), 2f)
                drawLine(RetroTheme.screenDark.copy(alpha = 0.7f), Offset(0f, center.y), Offset(size.width, center.y), 2f)
                drawCircle(RetroTheme.red, 5.dp.toPx(), center)
                drawCircle(RetroTheme.paper, 2.dp.toPx(), center)
            }
            Column(modifier = Modifier.padding(3.dp)) {
                PixelText("FIELD SCANNER", color = RetroTheme.screen, fontSize = 8.sp)
                PixelText("MAP MODULE", color = RetroTheme.muted, fontSize = 6.sp)
            }
            PixelText(
                "LOCATION FEED\nUNAVAILABLE",
                color = RetroTheme.screen,
                fontSize = 7.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(3.dp),
            )
        }
    }
}

@Composable
private fun PartyRack(
    party: List<HubMon>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    PixelPanel(modifier = modifier, accent = RetroTheme.blue) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PixelText("PARTY", color = RetroTheme.paper, fontSize = 8.sp)
                PixelText("${party.size}/6", color = RetroTheme.muted, fontSize = 6.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            repeat(3) { row ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    PartySlot(party.getOrNull(row), row, onSelect, Modifier.weight(1f).fillMaxHeight())
                    Spacer(modifier = Modifier.width(4.dp))
                    PartySlot(party.getOrNull(row + 3), row + 3, onSelect, Modifier.weight(1f).fillMaxHeight())
                }
                if (row != 2) Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun PartySlot(mon: HubMon?, index: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val clickable = if (mon != null) Modifier.clickable { onSelect(index) } else Modifier
    PixelPanel(modifier = modifier.then(clickable), accent = if (mon == null) RetroTheme.inkSoft else RetroTheme.red) {
        if (mon == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PixelText("--", color = RetroTheme.muted, fontSize = 8.sp)
            }
        } else {
            val context = LocalContext.current
            val sprite = remember(mon.speciesId) { SpriteAssets.frontSprite(context, mon.speciesId) }
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    PixelText("${index + 1}", color = RetroTheme.yellow, fontSize = 6.sp)
                    PixelText("L${mon.level}", color = RetroTheme.paper, fontSize = 6.sp)
                }
                PokeBallSprite(sprite = sprite, modifier = Modifier.size(39.dp).padding(top = 2.dp))
                PixelText(
                    mon.nickname.ifBlank { "UNKNOWN" }.uppercase().take(9),
                    color = RetroTheme.paper,
                    fontSize = 6.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                PixelHpBar(
                    fraction = if (mon.maxHp > 0) mon.currentHp.toFloat() / mon.maxHp else 0f,
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun PokeBallSprite(
    sprite: androidx.compose.ui.graphics.ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clip(CircleShape), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(RetroTheme.red)
            drawRect(RetroTheme.paper, Offset(0f, size.height / 2f), Size(size.width, size.height / 2f))
            drawRect(RetroTheme.ink, Offset(0f, size.height / 2f - 2.dp.toPx()), Size(size.width, 4.dp.toPx()))
            drawCircle(RetroTheme.ink, size.minDimension * 0.18f)
            drawCircle(RetroTheme.paper, size.minDimension * 0.10f)
        }
        if (sprite != null) {
            Image(bitmap = sprite, contentDescription = null, modifier = Modifier.fillMaxSize().padding(3.dp))
        } else {
            PixelText("?", color = RetroTheme.ink, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FooterModule(label: String, accent: Color, modifier: Modifier = Modifier) {
    PixelPanel(modifier = modifier, accent = accent) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PixelText(label, color = RetroTheme.paper, fontSize = 8.sp)
        }
    }
}

@Composable
private fun BatteryIcon(percent: Int) {
    val fill = when {
        percent > 50 -> RetroTheme.hpGreen
        percent > 20 -> RetroTheme.hpYellow
        else -> RetroTheme.hpRed
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = 23.dp, height = 11.dp)) {
            val bodyWidth = size.width * 0.86f
            drawRect(RetroTheme.paper, size = Size(bodyWidth, size.height))
            drawRect(RetroTheme.ink, topLeft = Offset(2.dp.toPx(), 2.dp.toPx()), size = Size(bodyWidth - 4.dp.toPx(), size.height - 4.dp.toPx()))
            drawRect(
                fill,
                topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                size = Size((bodyWidth - 6.dp.toPx()) * (percent.coerceIn(0, 100) / 100f), size.height - 6.dp.toPx()),
            )
            drawRect(RetroTheme.paper, topLeft = Offset(bodyWidth, size.height * 0.3f), size = Size(size.width - bodyWidth, size.height * 0.4f))
        }
        Spacer(modifier = Modifier.width(3.dp))
        PixelText("$percent%", color = RetroTheme.paper, fontSize = 6.sp)
    }
}
