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
import com.unboundds.companion.pokemon.BaseStats
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
    baseStats: BaseStats,
    phase: Int,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val sprite = remember(mon.speciesId) { SpriteAssets.frontSprite(context, mon.speciesId) }
    val speciesName = names.speciesName(mon.speciesId)
    val displayName = mon.nickname.ifBlank { speciesName }
    val baseEntry = baseStats.entry(mon.speciesId)
    val types = listOfNotNull(baseEntry?.type1, baseEntry?.type2)
    val (weaknesses, resists) = remember(baseEntry?.type1, baseEntry?.type2) {
        weaknessesAndResists(baseEntry?.type1, baseEntry?.type2)
    }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelText(displayName.uppercase(), color = DetailTextLight, fontSize = 14.sp)
                    types.forEach { type ->
                        Spacer(modifier = Modifier.width(6.dp))
                        TypeBadge(type)
                    }
                }
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
                    PixelText("Lv ${mon.level}", color = DetailTextLight, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    PixelText(
                        "Item: ${names.itemName(mon.heldItemId)}   Ability: ${names.abilityName(mon.abilityId)}",
                        color = DetailTextDim,
                        fontSize = 8.sp,
                    )
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
                    StatsRow(
                        listOf(
                            "ATK" to mon.attack,
                            "DEF" to mon.defense,
                            "SPD" to mon.speed,
                            "SP.ATK" to mon.spAttack,
                            "SP.DEF" to mon.spDefense,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            DetailPanelBox {
                Column {
                    PixelText("MOVES", color = DetailTextDim, fontSize = 9.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    val rows = mon.moves.mapIndexed { i, moveId -> moveId to mon.pp.getOrElse(i) { 0 } }.chunked(2)
                    rows.forEachIndexed { rowIndex, rowSlots ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowSlots.forEach { (moveId, pp) ->
                                if (moveId != 0) {
                                    MoveCard(
                                        name = names.moveName(moveId),
                                        type = moveData.type(moveId),
                                        pp = pp,
                                        ppMax = moveData.ppMax(moveId),
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        if (rowIndex != rows.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            DetailPanelBox {
                Column {
                    PixelText("WEAKNESSES", color = DetailTextDim, fontSize = 9.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    TypeBadgeRow(weaknesses)
                    Spacer(modifier = Modifier.height(10.dp))
                    PixelText("RESISTS", color = DetailTextDim, fontSize = 9.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    TypeBadgeRow(resists)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

/** All stats spread evenly across the panel's full width, each as a compact label/value pair. */
@Composable
private fun StatsRow(stats: List<Pair<String, Int>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        stats.forEach { (label, value) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PixelText(label, color = DetailTextDim, fontSize = 8.sp)
                Spacer(modifier = Modifier.height(2.dp))
                PixelText(value.toString(), color = DetailTextLight, fontSize = 10.sp)
            }
        }
    }
}

private val TypeColors = mapOf(
    "Normal" to Color(0xFFA8A878),
    "Fire" to Color(0xFFF08030),
    "Water" to Color(0xFF6890F0),
    "Electric" to Color(0xFFF8D030),
    "Grass" to Color(0xFF78C850),
    "Ice" to Color(0xFF98D8D8),
    "Fighting" to Color(0xFFC03028),
    "Poison" to Color(0xFFA040A0),
    "Ground" to Color(0xFFE0C068),
    "Flying" to Color(0xFFA890F0),
    "Psychic" to Color(0xFFF85888),
    "Bug" to Color(0xFFA8B820),
    "Rock" to Color(0xFFB8A038),
    "Ghost" to Color(0xFF705898),
    "Dragon" to Color(0xFF7038F8),
    "Dark" to Color(0xFF705848),
    "Steel" to Color(0xFFB8B8D0),
    "Fairy" to Color(0xFFEE99AC),
)

private fun typeColor(type: String): Color = TypeColors[type] ?: DetailTextDim

private val TypeAbbrev = mapOf(
    "Normal" to "NOR",
    "Fire" to "FIR",
    "Water" to "WAT",
    "Electric" to "ELE",
    "Grass" to "GRA",
    "Ice" to "ICE",
    "Fighting" to "FIG",
    "Poison" to "POI",
    "Ground" to "GRD",
    "Flying" to "FLY",
    "Psychic" to "PSY",
    "Bug" to "BUG",
    "Rock" to "ROC",
    "Ghost" to "GHO",
    "Dragon" to "DRA",
    "Dark" to "DAR",
    "Steel" to "STE",
    "Fairy" to "FAI",
)

/**
 * Standard (Gen 6+, Fairy-inclusive) type chart, keyed by DEFENDING type ->
 * (ATTACKING type -> damage multiplier). Only non-1x entries are listed;
 * anything absent is assumed neutral (1x).
 */
private val TypeChart: Map<String, Map<String, Float>> = mapOf(
    "Normal" to mapOf("Fighting" to 2f, "Ghost" to 0f),
    "Fire" to mapOf("Water" to 2f, "Ground" to 2f, "Rock" to 2f, "Fire" to 0.5f, "Grass" to 0.5f, "Ice" to 0.5f, "Bug" to 0.5f, "Steel" to 0.5f, "Fairy" to 0.5f),
    "Water" to mapOf("Electric" to 2f, "Grass" to 2f, "Fire" to 0.5f, "Water" to 0.5f, "Ice" to 0.5f, "Steel" to 0.5f),
    "Electric" to mapOf("Ground" to 2f, "Electric" to 0.5f, "Flying" to 0.5f, "Steel" to 0.5f),
    "Grass" to mapOf("Fire" to 2f, "Ice" to 2f, "Poison" to 2f, "Flying" to 2f, "Bug" to 2f, "Water" to 0.5f, "Electric" to 0.5f, "Grass" to 0.5f, "Ground" to 0.5f),
    "Ice" to mapOf("Fire" to 2f, "Fighting" to 2f, "Rock" to 2f, "Steel" to 2f, "Ice" to 0.5f),
    "Fighting" to mapOf("Flying" to 2f, "Psychic" to 2f, "Fairy" to 2f, "Bug" to 0.5f, "Rock" to 0.5f, "Dark" to 0.5f),
    "Poison" to mapOf("Ground" to 2f, "Psychic" to 2f, "Grass" to 0.5f, "Fighting" to 0.5f, "Poison" to 0.5f, "Bug" to 0.5f, "Fairy" to 0.5f),
    "Ground" to mapOf("Water" to 2f, "Grass" to 2f, "Ice" to 2f, "Poison" to 0.5f, "Rock" to 0.5f, "Electric" to 0f),
    "Flying" to mapOf("Electric" to 2f, "Ice" to 2f, "Rock" to 2f, "Grass" to 0.5f, "Fighting" to 0.5f, "Bug" to 0.5f, "Ground" to 0f),
    "Psychic" to mapOf("Bug" to 2f, "Ghost" to 2f, "Dark" to 2f, "Fighting" to 0.5f, "Psychic" to 0.5f),
    "Bug" to mapOf("Fire" to 2f, "Flying" to 2f, "Rock" to 2f, "Grass" to 0.5f, "Fighting" to 0.5f, "Ground" to 0.5f),
    "Rock" to mapOf("Water" to 2f, "Grass" to 2f, "Fighting" to 2f, "Ground" to 2f, "Steel" to 2f, "Normal" to 0.5f, "Fire" to 0.5f, "Poison" to 0.5f, "Flying" to 0.5f),
    "Ghost" to mapOf("Ghost" to 2f, "Dark" to 2f, "Poison" to 0.5f, "Bug" to 0.5f, "Normal" to 0f, "Fighting" to 0f),
    "Dragon" to mapOf("Ice" to 2f, "Dragon" to 2f, "Fairy" to 2f, "Fire" to 0.5f, "Water" to 0.5f, "Grass" to 0.5f, "Electric" to 0.5f),
    "Dark" to mapOf("Fighting" to 2f, "Bug" to 2f, "Fairy" to 2f, "Ghost" to 0.5f, "Dark" to 0.5f, "Psychic" to 0f),
    "Steel" to mapOf("Fire" to 2f, "Fighting" to 2f, "Ground" to 2f, "Normal" to 0.5f, "Grass" to 0.5f, "Ice" to 0.5f, "Flying" to 0.5f, "Psychic" to 0.5f, "Bug" to 0.5f, "Rock" to 0.5f, "Dragon" to 0.5f, "Steel" to 0.5f, "Fairy" to 0.5f, "Poison" to 0f),
    "Fairy" to mapOf("Poison" to 2f, "Steel" to 2f, "Fighting" to 0.5f, "Bug" to 0.5f, "Dark" to 0.5f, "Dragon" to 0f),
)

private fun typeEffectiveness(defenderTypes: List<String>, attackerType: String): Float =
    defenderTypes.fold(1f) { acc, defType -> acc * (TypeChart[defType]?.get(attackerType) ?: 1f) }

/** Combines a (possibly dual-type) Pokemon's types into weakness/resist lists (immunities count as resists). */
private fun weaknessesAndResists(type1: String?, type2: String?): Pair<List<String>, List<String>> {
    val types = listOfNotNull(type1, type2)
    if (types.isEmpty()) return emptyList<String>() to emptyList()
    val weak = mutableListOf<String>()
    val resist = mutableListOf<String>()
    TypeChart.keys.forEach { attackerType ->
        when {
            typeEffectiveness(types, attackerType) > 1f -> weak += attackerType
            typeEffectiveness(types, attackerType) < 1f -> resist += attackerType
        }
    }
    return weak to resist
}

@Composable
private fun TypeBadge(type: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(typeColor(type), RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        PixelText(TypeAbbrev[type] ?: type.take(3).uppercase(), color = Color.White, fontSize = 7.sp)
    }
}

/** Wraps type badges into rows of 6 so long weakness/resist lists don't overflow the panel width. */
@Composable
private fun TypeBadgeRow(types: List<String>) {
    if (types.isEmpty()) {
        PixelText("NONE", color = DetailTextDim, fontSize = 8.sp)
        return
    }
    val rows = types.chunked(6)
    rows.forEachIndexed { rowIndex, rowTypes ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            rowTypes.forEach { TypeBadge(it) }
        }
        if (rowIndex != rows.lastIndex) Spacer(modifier = Modifier.height(4.dp))
    }
}

/** One cell of the 2x2 move grid: name (tinted by type), type label, and PP. */
@Composable
private fun MoveCard(name: String, type: String, pp: Int, ppMax: Int, modifier: Modifier = Modifier) {
    val color = typeColor(type)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, color, RoundedCornerShape(4.dp))
            .padding(8.dp),
    ) {
        Column {
            PixelText(name.uppercase(), color = color, fontSize = 9.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PixelText(type.uppercase(), color = DetailTextDim, fontSize = 7.sp)
                PixelText("PP $pp/$ppMax", color = DetailTextDim, fontSize = 7.sp)
            }
        }
    }
}

@Composable
private fun DetailPanelBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
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
