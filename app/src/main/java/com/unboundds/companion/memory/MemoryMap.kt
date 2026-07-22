package com.unboundds.companion.memory

import android.content.Context
import org.json.JSONObject

/** A single named byte anchor from the memory map. */
data class Anchor(
    val name: String,
    val address: Int,
    val size: Int,
    val confidence: String,
    val note: String?,
)

/** Party layout: base address + stride, so slot N = firstSlotAddress + N * slotStride. */
data class PartyLayout(
    val firstSlotAddress: Int,
    val slotStride: Int,
    val slotCount: Int,
    val confidence: String,
)

data class MemoryMap(
    val unboundVersion: String,
    val baseGame: String,
    val party: PartyLayout,
    val anchors: List<Anchor>,
) {
    companion object {
        /** Loads the bundled seed map from assets/unbound_memory_map.json. */
        fun load(context: Context): MemoryMap {
            val json = context.assets.open("unbound_memory_map.json")
                .bufferedReader().use { it.readText() }
            val root = JSONObject(json)

            val partyObj = root.getJSONObject("party")
            val party = PartyLayout(
                firstSlotAddress = partyObj.getString("firstSlotAddress").parseHex(),
                slotStride = partyObj.getInt("slotStride"),
                slotCount = partyObj.getInt("slotCount"),
                confidence = partyObj.getString("confidence"),
            )

            val anchorsArr = root.getJSONArray("anchors")
            val anchors = (0 until anchorsArr.length()).map { i ->
                val a = anchorsArr.getJSONObject(i)
                Anchor(
                    name = a.getString("name"),
                    address = a.getString("address").parseHex(),
                    size = a.getInt("size"),
                    confidence = a.getString("confidence"),
                    note = if (a.has("note")) a.getString("note") else null,
                )
            }

            return MemoryMap(
                unboundVersion = root.getString("unboundVersion"),
                baseGame = root.getString("baseGame"),
                party = party,
                anchors = anchors,
            )
        }
    }
}

/** Parses "0x02024284" or "02024284" into an Int. */
fun String.parseHex(): Int = removePrefix("0x").toLong(16).toInt()
