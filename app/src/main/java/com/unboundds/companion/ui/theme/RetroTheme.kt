package com.unboundds.companion.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unboundds.companion.R

/** Shared pixel-art palette and UI primitives for the companion's Pokédex shell. */
object RetroTheme {
    val pixelFont = FontFamily(Font(R.font.press_start_2p, FontWeight.Normal))

    val ink = Color(0xFF101827)
    val inkSoft = Color(0xFF20324B)
    val space = Color(0xFF071226)
    val panel = Color(0xFF173356)
    val panelDeep = Color(0xFF0D2340)
    val panelHighlight = Color(0xFF3D6B92)
    val screen = Color(0xFF9ADAB8)
    val screenDark = Color(0xFF337A70)
    val paper = Color(0xFFF6F0D8)
    val muted = Color(0xFFAFC7D6)
    val red = Color(0xFFD94245)
    val redDark = Color(0xFF8C2538)
    val blue = Color(0xFF377FD1)
    val teal = Color(0xFF37A89A)
    val yellow = Color(0xFFF2C54B)

    // Kept for the legacy inspector screen as well as the shared HP bar.
    val background = space
    val panelBorder = ink
    val panelBorderInner = panelHighlight
    val text = ink
    val textOnDark = paper
    val accent = red

    val hpGreen = Color(0xFF6DDB6E)
    val hpYellow = Color(0xFFF4C64C)
    val hpRed = Color(0xFFE95755)
    val hpTrack = Color(0xFF0A1728)
}

@Composable
fun PixelText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RetroTheme.text,
    fontSize: androidx.compose.ui.unit.TextUnit = 10.sp,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = TextStyle(
            fontFamily = RetroTheme.pixelFont,
            fontSize = fontSize,
            lineHeight = fontSize * 1.4f,
        ),
    )
}

/** Full-screen navy shell with subtle scanlines, giving every live screen a GBA display texture. */
@Composable
fun PokedexShell(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(RetroTheme.space)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stripeHeight = 3.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawRect(
                    color = Color(0x18000000),
                    topLeft = Offset(0f, y),
                    size = Size(size.width, stripeHeight),
                )
                y += stripeHeight * 2f
            }
        }
        content()
    }
}

/** Chunky three-layer frame inspired by FireRed's menus and a handheld Pokédex casing. */
@Composable
fun PixelPanel(
    modifier: Modifier = Modifier,
    accent: Color = RetroTheme.blue,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(RetroTheme.ink, RectangleShape)
            .padding(2.dp)
            .background(accent, RectangleShape)
            .padding(2.dp)
            .background(RetroTheme.panel, RectangleShape)
            .border(1.dp, RetroTheme.panelHighlight, RectangleShape)
            .padding(7.dp),
    ) {
        content()
    }
}

/** Compatibility wrapper used by the older dev-facing party list. */
@Composable
fun RetroPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = PixelPanel(modifier = modifier, accent = RetroTheme.blue, content = content)

@Composable
fun TypeBadge(
    type: String,
    modifier: Modifier = Modifier,
) {
    val color = when (type.uppercase()) {
        "FIRE" -> Color(0xFFE76F51)
        "WATER" -> Color(0xFF4D96E8)
        "GRASS" -> Color(0xFF69B96D)
        "ELECTRIC" -> Color(0xFFE1B53D)
        "ICE" -> Color(0xFF6BC5C6)
        "FIGHTING" -> Color(0xFFC95C55)
        "POISON" -> Color(0xFF9B67BC)
        "GROUND" -> Color(0xFFC69A5B)
        "FLYING" -> Color(0xFF809EDE)
        "PSYCHIC" -> Color(0xFFDB6D9B)
        "BUG" -> Color(0xFFA5B640)
        "ROCK" -> Color(0xFF9C8656)
        "GHOST" -> Color(0xFF6E6AA7)
        "DRAGON" -> Color(0xFF7668D3)
        "DARK" -> Color(0xFF655A58)
        "STEEL" -> Color(0xFF8FA4AF)
        "FAIRY" -> Color(0xFFD98CC0)
        else -> Color(0xFFA6A1A0)
    }
    Box(
        modifier = modifier
            .background(RetroTheme.ink, RectangleShape)
            .padding(1.dp)
            .background(color, RectangleShape)
            .padding(horizontal = 4.dp, vertical = 3.dp),
    ) {
        PixelText(type.uppercase(), color = RetroTheme.ink, fontSize = 6.sp)
    }
}

/** Classic segmented HP bar with sharp corners and a high-contrast border. */
@Composable
fun PixelHpBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    val color = when {
        clamped > 0.5f -> RetroTheme.hpGreen
        clamped > 0.2f -> RetroTheme.hpYellow
        else -> RetroTheme.hpRed
    }
    Box(
        modifier = modifier
            .height(9.dp)
            .background(RetroTheme.ink, RectangleShape)
            .padding(2.dp)
            .background(RetroTheme.hpTrack, RectangleShape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .height(5.dp)
                .background(color, RectangleShape),
        )
    }
}
