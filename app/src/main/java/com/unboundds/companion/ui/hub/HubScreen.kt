package com.unboundds.companion.ui.hub

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.BatteryManager
import android.os.SystemClock
import android.view.MotionEvent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import com.unboundds.companion.memory.MapNames
import com.unboundds.companion.ui.detail.PokemonDetailScreen
import com.unboundds.companion.ui.opponent.OpponentScreen
import com.unboundds.companion.ui.theme.GoldHighlight
import com.unboundds.companion.ui.theme.GoldOutline
import com.unboundds.companion.ui.theme.PixelText
import com.unboundds.companion.ui.theme.RetroTheme
import com.unboundds.companion.ui.theme.PortalCanvas
import com.unboundds.companion.ui.theme.portalPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Calendar

private const val PARTY_POLL_INTERVAL_MS = 10_000L
private const val CLOCK_BATTERY_POLL_INTERVAL_MS = 15_000L
private const val IDLE_DIM_DELAY_MS = 10_000L
private const val IDLE_DIM_BRIGHTNESS = 0.08f
private const val DEV_TOOLS_HOLD_MS = 3_000L

private val HubBackground = Color(0xFF000000)
private val HubPanel = Color(0xFF141414) // near-black, a touch lighter than the OLED background
private val HubTextLight = Color(0xFFF0EEDA)

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

internal suspend fun readPartyMons(
    client: RetroArchClient,
    layout: PartyLayout,
    baseStats: BaseStats,
): List<HubMon> {
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
fun HubScreen(onDevToolsRequested: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }
    val client = remember { RetroArchClient() }
    val map = remember { MemoryMap.load(context) }
    val names = remember { NameTables.load(context) }
    val baseStats = remember { BaseStats.load(context) }
    val moveData = remember { MoveData.load(context) }
    val mapNames = remember { MapNames.load(context) }
    val regionMapAnchor = remember { map.anchors.firstOrNull { it.name == "regionMapSectionId" } }

    var party by remember { mutableStateOf<List<HubMon>>(emptyList()) }
    var battery by remember { mutableIntStateOf(batteryPercent(context)) }
    var time by remember { mutableStateOf(clockText()) }
    var selectedSlot by remember { mutableStateOf<Int?>(null) }
    var showOpponentScreen by remember { mutableStateOf(false) }
    var mapName by remember { mutableStateOf("MAP") }
    var isStarted by remember { mutableStateOf(false) }
    var dimmed by remember { mutableStateOf(false) }
    var lastActivityMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    val scope = rememberCoroutineScope()
    var devToolsHoldJob by remember { mutableStateOf<Job?>(null) }
    val phase = portalPhase(
        enabled = isStarted && !dimmed && !showOpponentScreen && selectedSlot == null,
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
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
            var changed = false
            val updatedParty = readPartyMons(client, map.party, baseStats)
            if (updatedParty != party) {
                party = updatedParty
                changed = true
            }
            val anchor = regionMapAnchor
            if (anchor != null) {
                val result = client.readCoreMemory(anchor.address, anchor.size)
                val bytes = (result as? RetroArchClient.Result.Success)
                    ?.let { parseReadCoreMemoryResponse(it.response) }
                if (bytes != null && bytes.isNotEmpty()) {
                    val updatedMapName = mapNames.nameFor(bytes[0].toInt() and 0xFF)
                    if (updatedMapName != mapName) {
                        mapName = updatedMapName
                        changed = true
                    }
                }
            }
            if (changed) {
                dimmed = false
                lastActivityMs = SystemClock.elapsedRealtime()
            }
            delay(PARTY_POLL_INTERVAL_MS)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Observe without consuming, so regular taps still reach party cards and buttons.
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        devToolsHoldJob?.cancel()
                        devToolsHoldJob = scope.launch {
                            delay(DEV_TOOLS_HOLD_MS)
                            onDevToolsRequested()
                        }
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> devToolsHoldJob?.cancel()
                }
                false
            },
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HubBackground)
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelText(time, color = HubTextLight, fontSize = 9.sp)
            Spacer(modifier = Modifier.width(8.dp))
            BatteryIcon(percent = battery)
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight()
                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(6.dp), ambientColor = GoldHighlight, spotColor = GoldHighlight)
                    .background(HubPanel, RoundedCornerShape(6.dp))
                    .border(2.dp, GoldOutline, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                PixelText(mapName.uppercase(), color = Color(0xFFB0B8A8), fontSize = 16.sp)
            }

            // Two columns of three: right column = slots 1-3, left column (new) = slots 4-6.
            // Bottom-aligned so the grid sits down near the OPPONENT/DEX buttons.
            Row(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight()
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                PartyColumn(
                    mons = party.drop(3).take(3),
                    startIndex = 3,
                    phase = phase,
                    onSelect = { selectedSlot = it },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                PartyColumn(
                    mons = party.take(3),
                    startIndex = 0,
                    phase = phase,
                    onSelect = { selectedSlot = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth().height(44.dp)) {
            HubButton("OPPONENT", phase, onClick = { showOpponentScreen = true }, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(10.dp))
            HubButton("DEX", phase, modifier = Modifier.weight(1f))
        }
    }

    val detailMon = selectedSlot?.let { party.getOrNull(it) }
    if (detailMon != null) {
        PokemonDetailScreen(
            mon = detailMon,
            names = names,
            moveData = moveData,
            baseStats = baseStats,
            phase = phase,
            onClose = { selectedSlot = null },
        )
    }

    if (showOpponentScreen) {
        OpponentScreen(
            onClose = { showOpponentScreen = false },
            onDataChanged = {
                dimmed = false
                lastActivityMs = SystemClock.elapsedRealtime()
            },
        )
    }
    if (dimmed) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HubBackground.copy(alpha = 0.72f))
                .clickable {
                    dimmed = false
                    lastActivityMs = SystemClock.elapsedRealtime()
                },
        )
    }
    }
}

@Composable
private fun PartyColumn(
    mons: List<HubMon>,
    startIndex: Int,
    phase: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Half height so each of the 3 slots matches the original 1x6 layout's per-slot
    // size (fullHeight / 6) instead of stretching to fill the whole column.
    Column(modifier = modifier.fillMaxHeight(0.5f), horizontalAlignment = Alignment.CenterHorizontally) {
        repeat(3) { i ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                mons.getOrNull(i)?.let { mon -> MonCircle(mon, phase) { onSelect(startIndex + i) } }
            }
        }
    }
}

@Composable
private fun HubButton(label: String, phase: Int, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(8.dp), ambientColor = GoldHighlight, spotColor = GoldHighlight)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, GoldOutline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PortalCanvas(phase = phase, modifier = Modifier.matchParentSize())
        OutlinedPixelText(label, fontSize = 11.sp)
    }
}

@Composable
internal fun MonCircle(mon: HubMon, phase: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val sprite = remember(mon.speciesId) { SpriteAssets.frontSprite(context, mon.speciesId) }

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .shadow(elevation = 3.dp, shape = CircleShape, ambientColor = GoldHighlight, spotColor = GoldHighlight)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .border(2.dp, GoldOutline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PortalCanvas(phase = phase, modifier = Modifier.matchParentSize())
            if (sprite != null) {
                Image(bitmap = sprite, contentDescription = null, modifier = Modifier.size(42.dp))
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

/** Battery glyph, light-on-black: gold outline + cap, fill bar by charge. */
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
                color = GoldOutline,
                size = Size(bodyWidth, size.height),
                cornerRadius = CornerRadius(3f, 3f),
                style = Stroke(width = 3f),
            )
            drawRoundRect(
                color = GoldOutline,
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
