package com.unboundds.companion.pokemon

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Front sprites bundled from Dynamic Pokemon Expansion (github.com/DizzyEggg/
 * Dynamic-Pokemon-Expansion, WTFPL) at assets/sprites/front/<speciesId>.png.
 * Same source as the species name table, so ids line up. Not every species has
 * art (1201 of ~1243) -- missing ones return null and the caller shows a
 * placeholder.
 */
object SpriteAssets {
    private val cache = HashMap<Int, ImageBitmap?>()

    fun frontSprite(context: Context, speciesId: Int): ImageBitmap? {
        cache[speciesId]?.let { return it }
        if (cache.containsKey(speciesId)) return null // cached miss

        val bitmap = try {
            context.assets.open("sprites/front/$speciesId.png").use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: java.io.IOException) {
            null
        }
        cache[speciesId] = bitmap
        return bitmap
    }
}
