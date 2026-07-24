package com.gbapal.companion.ui.theme

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Nocturne: a quiet, compact dark theme for the hub/opponent/detail screens
 * -- a deep blue-grey ground, a single blurple accent used only as a
 * border/glow (never a flood fill, matching Nocturne's outlined-button
 * rule), soft rounded corners, and grotesque sans type. Modeled on a
 * Claude Design system of the same name. Inter itself isn't bundled in
 * this app, so system sans-serif stands in for its "grotesque" voice --
 * same approach RetroTheme already takes with its own pixel font being a
 * styled approximation rather than the genuine article.
 */
val NocturneBg = Color(0xFF161826)
val NocturneSurface = Color(0xFF232532)
val NocturneText = Color(0xFFE9E9ED)
val NocturneTextMuted = Color(0xFFA6A6B8)
val NocturneAccent = Color(0xFF9184D9)
val NocturneAccentGlow = Color(0x669184D9) // accent at ~40% alpha, for shadow/glow tints only

val NocturneFont = FontFamily.SansSerif

/** Plain Nocturne-styled label -- no stroke/outline trick needed since it only ever sits on flat surface fills now. */
@Composable
fun NocturneLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NocturneText,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = TextStyle(
            fontFamily = NocturneFont,
            fontWeight = fontWeight,
            fontSize = fontSize,
            lineHeight = fontSize * 1.3f,
        ),
    )
}
