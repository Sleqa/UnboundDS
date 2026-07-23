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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unboundds.companion.pokemon.MoveData
import com.unboundds.companion.pokemon.NameTables
import com.unboundds.companion.pokemon.SpriteAssets
import com.unboundds.companion.ui.hub.HubMon
import com.unboundds.companion.ui.theme.PixelHpBar
import com.unboundds.companion.ui.theme.PixelPanel
import com.unboundds.companion.ui.theme.PixelText
import com.unboundds.companion.ui.theme.PokedexShell
import com.unboundds.companion.ui.theme.RetroTheme
import com.unboundds.companion.ui.theme.TypeBadge

/** Full-screen party dossier, styled as a compact Gen 3 Pokédex data page. */
@Composable
fun PokemonDetailScreen(
    mon: HubMon,
    names: NameTables,
    moveData: MoveData,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val sprite = remember(mon.speciesId) { SpriteAssets.frontSprite(context, mon.speciesId) }
    val speciesName = names.speciesName(mon.speciesId)
    val displayName = mon.nickname.ifBlank { speciesName }

    PokedexShell {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            Header(displayName = displayName, speciesId = mon.speciesId, onClose = onClose)
            Spacer(modifier = Modifier.height(8.dp))
            PixelPanel(accent = RetroTheme.red, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpriteDisplay(sprite)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        PixelText(speciesName.uppercase(), color = RetroTheme.paper, fontSize = 10.sp)
                        Spacer(modifier = Modifier.height(5.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            mon.type1?.let { TypeBadge(it) }
                            mon.type2?.takeIf { it != mon.type1 }?.let { TypeBadge(it) }
                        }
                        Spacer(modifier = Modifier.height(7.dp))
                        PixelText("LEVEL ${mon.level}", color = RetroTheme.muted, fontSize = 7.sp)
                        Spacer(modifier = Modifier.height(3.dp))
                        PixelHpBar(
                            fraction = if (mon.maxHp > 0) mon.currentHp.toFloat() / mon.maxHp else 0f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        PixelText("HP ${mon.currentHp}/${mon.maxHp}", color = RetroTheme.paper, fontSize = 7.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                DataPanel("BATTLE STATS", RetroTheme.blue, Modifier.weight(1f)) {
                    StatRow("ATK", mon.attack)
                    StatRow("DEF", mon.defense)
                    StatRow("SPD", mon.speed)
                    StatRow("SP.ATK", mon.spAttack)
                    StatRow("SP.DEF", mon.spDefense)
                }
                Spacer(modifier = Modifier.width(8.dp))
                DataPanel("TRAINER DATA", RetroTheme.yellow, Modifier.weight(1f)) {
                    DataValue("HELD ITEM", names.itemName(mon.heldItemId))
                    Spacer(modifier = Modifier.height(8.dp))
                    DataValue("ABILITY", names.abilityName(mon.abilityId))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            DataPanel("MOVE SET", RetroTheme.teal, Modifier.fillMaxWidth().weight(1f)) {
                mon.moves.forEachIndexed { index, moveId ->
                    if (moveId != 0) {
                        MoveRow(
                            name = names.moveName(moveId),
                            type = moveData.type(moveId),
                            pp = mon.pp.getOrElse(index) { 0 },
                            ppMax = moveData.ppMax(moveId),
                        )
                        if (index != mon.moves.lastIndex) Spacer(modifier = Modifier.height(7.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(displayName: String, speciesId: Int, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PixelText("POKÉDEX DATA", color = RetroTheme.yellow, fontSize = 7.sp)
            Spacer(modifier = Modifier.height(2.dp))
            PixelText(displayName.uppercase(), color = RetroTheme.paper, fontSize = 13.sp)
        }
        PixelText("NO. ${speciesId.toString().padStart(4, '0')}", color = RetroTheme.muted, fontSize = 6.sp)
        Spacer(modifier = Modifier.width(7.dp))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(RetroTheme.red)
                .border(2.dp, RetroTheme.paper, CircleShape)
                .clickable(onClick = onClose)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            PixelText("X", color = RetroTheme.paper, fontSize = 8.sp)
        }
    }
}

@Composable
private fun SpriteDisplay(sprite: androidx.compose.ui.graphics.ImageBitmap?) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .background(RetroTheme.ink, CircleShape)
            .padding(3.dp)
            .background(RetroTheme.paper, CircleShape)
            .padding(3.dp)
            .background(RetroTheme.red, CircleShape)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .background(RetroTheme.panelDeep, CircleShape)
                .border(2.dp, RetroTheme.yellow, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (sprite != null) {
                Image(bitmap = sprite, contentDescription = null, modifier = Modifier.size(62.dp))
            } else {
                PixelText("?", color = RetroTheme.paper, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun DataPanel(title: String, accent: Color, modifier: Modifier, content: @Composable () -> Unit) {
    PixelPanel(modifier = modifier, accent = accent) {
        Column(modifier = Modifier.fillMaxSize()) {
            PixelText(title, color = RetroTheme.muted, fontSize = 7.sp)
            Spacer(modifier = Modifier.height(7.dp))
            content()
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        PixelText(label, color = RetroTheme.muted, fontSize = 7.sp)
        PixelText(value.toString(), color = RetroTheme.paper, fontSize = 7.sp)
    }
    Spacer(modifier = Modifier.height(5.dp))
}

@Composable
private fun DataValue(label: String, value: String) {
    PixelText(label, color = RetroTheme.muted, fontSize = 6.sp)
    Spacer(modifier = Modifier.height(3.dp))
    PixelText(value.uppercase(), color = RetroTheme.paper, fontSize = 7.sp)
}

@Composable
private fun MoveRow(name: String, type: String, pp: Int, ppMax: Int) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TypeBadge(type)
        Spacer(modifier = Modifier.width(6.dp))
        PixelText(name.uppercase(), color = RetroTheme.paper, fontSize = 8.sp, modifier = Modifier.weight(1f))
        PixelText("$pp/$ppMax", color = RetroTheme.yellow, fontSize = 7.sp)
    }
}
