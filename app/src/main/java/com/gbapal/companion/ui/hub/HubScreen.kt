package com.gbapal.companion.ui.hub

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gbapal.companion.memory.MemoryMap
import com.gbapal.companion.memory.PartyLayout
import com.gbapal.companion.network.RetroArchClient
import com.gbapal.companion.network.parseReadCoreMemoryResponse
import com.gbapal.companion.pokemon.BaseStats
import com.gbapal.companion.pokemon.Gen3Decrypt
import com.gbapal.companion.pokemon.MoveData
import com.gbapal.companion.pokemon.NameTables
import com.gbapal.companion.pokemon.PartyDecoder
import com.gbapal.companion.pokemon.SpriteAssets
import com.gbapal.companion.ui.detail.PokemonDetailScreen
import com.gbapal.companion.ui.opponent.OpponentScreen
import com.gbapal.companion.ui.theme.FrameGlow
import com.gbapal.companion.ui.theme.FrameOutline
import com.gbapal.companion.ui.theme.PixelText
import com.gbapal.companion.ui.theme.RetroTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Calendar

private const val PARTY_POLL_INTERVAL_MS = 10_000L
private const val CLOCK_BATTERY_POLL_INTERVAL_MS = 15_000L

private val HubBackground = Color(0xFF000000)
private val HubPanel = Color(0xFF1A1A1A)
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
    val client = remember { RetroArchClient() }
    val map = remember { MemoryMap.load(context) }
    val names = remember { NameTables.load(context) }
    val baseStats = remember { BaseStats.load(context) }
    val moveData = remember { MoveData.load(context) }

    val battleFlagAnchor = remember { map.anchors.firstOrNull { it.name == "battleStateFlag" } }

    var party by remember { mutableStateOf<List<HubMon>>(emptyList()) }
    var lastBattleFlag by remember { mutableStateOf(0) }
    var battery by remember { mutableIntStateOf(batteryPercent(context)) }
    var time by remember { mutableStateOf(clockText()) }
    var selectedSlot by remember { mutableStateOf<Int?>(null) }
    var showOpponentScreen by remember { mutableStateOf(false) }
    var isStarted by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isStarted, showOpponentScreen) {
        if (!isStarted) return@LaunchedEffect
        while (isActive) {
            val updatedParty = readPartyMons(client, map.party, baseStats)
            if (updatedParty != party) {
                party = updatedParty
            }
            if (!showOpponentScreen) {
                val anchor = battleFlagAnchor
                if (anchor != null) {
                    val result = client.readCoreMemory(anchor.address, anchor.size)
                    val flagByte = (result as? RetroArchClient.Result.Success)
                        ?.let { parseReadCoreMemoryResponse(it.response) }
                        ?.firstOrNull()
                        ?.let { it.toInt() and 0xFF }
                        ?: 0
                    if (lastBattleFlag == 0 && flagByte != 0) {
                        showOpponentScreen = true
                    }
                    lastBattleFlag = flagByte
                }
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

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HubBackground)
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 3.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TopBarBanner {
                PixelText(time, color = Color.White, fontSize = 9.sp)
                Spacer(modifier = Modifier.width(8.dp))
                BatteryIcon(percent = battery)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "⚙",
                    color = Color.White,
                    fontSize = 17.sp,
                    modifier = Modifier.clickable(onClick = onDevToolsRequested),
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Party banners pop out from the left/right edges, tips pointing in toward
        // the middle but stopping at 40% of the row each, leaving a gap in the
        // center -- slots 1-3 on the left, 4-6 on the right.
        Row(modifier = Modifier.weight(1f)) {
            BannerColumn(
                mons = party.take(3),
                startIndex = 0,
                pointRight = true,
                onSelect = { selectedSlot = it },
                modifier = Modifier.weight(0.4f).fillMaxHeight(),
            )
            Spacer(modifier = Modifier.weight(0.2f))
            BannerColumn(
                mons = party.drop(3).take(3),
                startIndex = 3,
                pointRight = false,
                onSelect = { selectedSlot = it },
                modifier = Modifier.weight(0.4f).fillMaxHeight(),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth().height(44.dp)) {
            HubButton("OPPONENT", onClick = { showOpponentScreen = true }, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(10.dp))
            HubButton("DEX", modifier = Modifier.weight(1f))
        }
    }

    val detailMon = selectedSlot?.let { party.getOrNull(it) }
    if (detailMon != null) {
        PokemonDetailScreen(
            mon = detailMon,
            names = names,
            moveData = moveData,
            baseStats = baseStats,
            onClose = { selectedSlot = null },
        )
    }

    if (showOpponentScreen) {
        OpponentScreen(onClose = { showOpponentScreen = false })
    }
    }
}

@Composable
internal fun BannerColumn(
    mons: List<HubMon>,
    startIndex: Int,
    pointRight: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Bottom-anchored: the bottom banner stays put and any leftover column
    // height collapses above it, pulling the other rows down closer to it
    // instead of spreading all three evenly across the full height.
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
    ) {
        repeat(3) { i ->
            mons.getOrNull(i)?.let { mon ->
                MonBanner(mon, pointRight) { onSelect(startIndex + i) }
            }
        }
    }
}

/**
 * A banner shape whose inner edge (the side facing the middle of the screen)
 * is a single slant rather than a pointed tip: the top edge runs the full
 * width while the bottom edge stops short, joined by one diagonal line, and
 * every corner is rounded. [pointRight] controls which side the slant faces.
 */
private fun bannerShape(pointRight: Boolean) = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val slant = w * 0.14f
    val corner = minOf(w, h) * 0.22f
    val points = if (pointRight) {
        listOf(Offset(0f, 0f), Offset(w, 0f), Offset(w - slant, h), Offset(0f, h))
    } else {
        listOf(Offset(w, 0f), Offset(0f, 0f), Offset(slant, h), Offset(w, h))
    }
    roundedPolygon(points, corner)
}

/** Traces a closed polygon with each corner replaced by a rounded curve. */
private fun Path.roundedPolygon(points: List<Offset>, radius: Float) {
    val n = points.size
    for (i in 0 until n) {
        val prev = points[(i - 1 + n) % n]
        val curr = points[i]
        val next = points[(i + 1) % n]
        val r = radius
            .coerceAtMost((curr - prev).getDistance() / 2f)
            .coerceAtMost((next - curr).getDistance() / 2f)
        val approach = curr.towards(prev, r)
        val depart = curr.towards(next, r)
        if (i == 0) moveTo(approach.x, approach.y) else lineTo(approach.x, approach.y)
        quadraticTo(curr.x, curr.y, depart.x, depart.y)
    }
    close()
}

private fun Offset.towards(target: Offset, distance: Float): Offset {
    val delta = target - this
    val len = delta.getDistance()
    return if (len <= 0f) this else this + delta * (distance / len)
}

/** Same pop-out banner style as the party rows, sized to hug the clock/battery/cog row. */
@Composable
private fun TopBarBanner(content: @Composable RowScope.() -> Unit) {
    val shape = remember { bannerShape(pointRight = false) }
    Box(
        modifier = Modifier
            .shadow(elevation = 3.dp, shape = shape, ambientColor = FrameGlow, spotColor = FrameGlow)
            .clip(shape)
            .background(HubPanel)
            .border(2.dp, FrameOutline, shape),
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun HubButton(label: String, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(8.dp), ambientColor = FrameGlow, spotColor = FrameGlow)
            .clip(RoundedCornerShape(8.dp))
            .background(HubPanel)
            .border(2.dp, FrameOutline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        OutlinedPixelText(label, fontSize = 11.sp)
    }
}

@Composable
internal fun MonBanner(mon: HubMon, pointRight: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val sprite = remember(mon.speciesId) { SpriteAssets.frontSprite(context, mon.speciesId) }
    val shape = remember(pointRight) { bannerShape(pointRight) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(elevation = 3.dp, shape = shape, ambientColor = FrameGlow, spotColor = FrameGlow)
            .clip(shape)
            .background(HubPanel)
            .clickable(onClick = onClick)
            .border(2.dp, FrameOutline, shape),
        contentAlignment = if (pointRight) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (sprite != null) {
                Image(
                    bitmap = sprite,
                    contentDescription = null,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(46.dp),
                )
            } else {
                Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    PixelText("?", color = HubTextLight, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                OutlinedPixelText(mon.nickname.uppercase(), fontSize = 9.sp)
                OutlinedPixelText("Lv${mon.level}", fontSize = 9.sp)
            }
        }
    }
}

/** White pixel text with a dark stroke outline so it reads over the panel background. */
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

/** Battery glyph, light-on-black: white outline + cap, fill bar by charge. */
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
                color = Color.White,
                size = Size(bodyWidth, size.height),
                cornerRadius = CornerRadius(3f, 3f),
                style = Stroke(width = 3f),
            )
            drawRoundRect(
                color = Color.White,
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
        PixelText("$percent%", color = Color.White, fontSize = 8.sp)
    }
}
