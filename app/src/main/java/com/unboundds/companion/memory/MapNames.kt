package com.unboundds.companion.memory

import android.content.Context
import org.json.JSONObject

/**
 * regionMapSectionId (0x02036E10) -> Unbound location name. Crowdsourced by
 * hand since Unbound's real table isn't public -- grows as new id/name pairs
 * get confirmed by walking around and reading the value live.
 */
class MapNames private constructor(private val names: Map<Int, String>) {
    fun nameFor(id: Int): String = names[id] ?: "Unknown area #$id"

    companion object {
        fun load(context: Context): MapNames {
            val json = context.assets.open("unbound_map_names.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            val map = HashMap<Int, String>(obj.length())
            obj.keys().forEach { key ->
                if (key != "_note") map[key.toInt()] = obj.getString(key)
            }
            return MapNames(map)
        }
    }
}
