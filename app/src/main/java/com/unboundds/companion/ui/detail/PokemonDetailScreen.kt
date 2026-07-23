package com.unboundds.companion.ui.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unboundds.companion.pokemon.MoveData
import com.unboundds.companion.pokemon.NameTables
import com.unboundds.companion.pokemon.SpriteAssets
import com.unboundds.companion.ui.hub.HubMon
import com.unboundds.companion.ui.hub.OutlinedPixelText
import com.unboundds.companion.ui.theme.GoldHighlight
import com.unboundds.companion.ui.theme.GoldOutline
import com.unboundds.companion.ui.theme.PixelHpBar
import com.unboundds.companion.ui.theme.PixelText
import com.unboundds.companion.ui.theme.PortalCanvas

private val DetailBackground = Color(0xFF000000)
private val DetailPanel = Color(0xFF141414)
private val DetailTextLight = Color(0xFFF0EEDA)
private val DetailTextDim = Color(0xFFB0B8A8)

/** Full-screen retro-styled summary for one party Pokemon: stats, moveset+PP, item, ability. */
@Composable
fun PokemonDetailScreen(
    mon: HubMon,
    names: NameTables,
    moveData: MoveData,
    phase: Int,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val sprite = remember(mon.speciesId) { SpriteAssets.frontSprite(context, mon.speciesId) }
    val speciesName = names.speciesName(mon.speciesId)
    val displayName = mon.nickname.ifBlank { speciesName }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DetailBackground)
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelText(displayName.uppercase(), color = DetailTextLight, fontSize = 14.sp)
                CloseButton(onClose)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .shadow(elevation = 4.dp, shape = CircleShape, ambientColor = GoldHighlight, spotColor = GoldHighlight)
                        .clip(CircleShape)
                        .border(2.dp, GoldOutline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    PortalCanvas(phase = phase, modifier = Modifier.matchParentSize())
                    if (sprite != null) {
                        Image(bitmap = sprite, contentDescription = null, modifier = Modifier.size(72.dp))
                    } else {
                        PixelText("?", color = DetailTextLight, fontSize = 20.sp)
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    PixelText(speciesName.uppercase(), color = DetailTextDim, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    PixelText("Lv ${mon.level}", color = DetailTextLight, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    PixelHpBar(
                        fraction = if (mon.maxHp > 0) mon.currentHp.toFloat() / mon.maxHp else 0f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    PixelText("HP ${mon.currentHp}/${mon.maxHp}", color = DetailTextLight, fontSize = 9.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DetailPanelBox {
                Column {
                    PixelText("STATS", color = DetailTextDim, fontSize = 9.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatColumn(
                            listOf("ATK" to mon.attack, "DEF" to mon.defense, "SPD" to mon.speed),
                            modifier = Modifier.weight(1f),
                        )
                        StatColumn(
                            listOf("SP.ATK" to mon.spAttack, "SP.DEF" to mon.spDefense),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            DetailPanelBox {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        PixelText("ITEM", color = DetailTextDim, fontSize = 9.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        PixelText(names.itemName(mon.heldItemId), color = DetailTextLight, fontSize = 9.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        PixelText("ABILITY", color = DetailTextDim, fontSize = 9.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        PixelText(names.abilityName(mon.abilityId), color = DetailTextLight, fontSize = 9.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            DetailPanelBox {
                Column {
                    PixelText("MOVES", color = DetailTextDim, fontSize = 9.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    mon.moves.forEachIndexed { i, moveId ->
                        if (moveId != 0) {
                            MoveRow(
                                name = names.moveName(moveId),
                                type = moveData.type(moveId),
                                pp = mon.pp.getOrElse(i) { 0 },
                                ppMax = moveData.ppMax(moveId),
                            )
                            if (i != mon.moves.lastIndex) Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun StatColumn(stats: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        stats.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                PixelText(label, color = DetailTextDim, fontSize = 9.sp, modifier = Modifier.weight(1f))
                PixelText(value.toString(), color = DetailTextLight, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun MoveRow(name: String, type: String, pp: Int, ppMax: Int) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PixelText(name.uppercase(), color = DetailTextLight, fontSize = 9.sp, modifier = Modifier.weight(1f))
        PixelText(type.uppercase(), color = DetailTextDim, fontSize = 8.sp, modifier = Modifier.padding(end = 10.dp))
        PixelText("PP $pp/$ppMax", color = DetailTextDim, fontSize = 8.sp)
    }
}

@Composable
private fun DetailPanelBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(6.dp), ambientColor = GoldHighlight, spotColor = GoldHighlight)
            .background(DetailPanel, RoundedCornerShape(6.dp))
            .border(2.dp, GoldOutline, RoundedCornerShape(6.dp))
            .padding(10.dp),
    ) {
        content()
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(2.dp, GoldOutline, RoundedCornerShape(6.dp))
            .clickable(onClick = onClose)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        OutlinedPixelText("CLOSE", fontSize = 9.sp)
    }
}
