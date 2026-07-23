package com.unboundds.companion.memory

import com.unboundds.companion.network.RetroArchClient
import com.unboundds.companion.network.parseReadCoreMemoryResponse

/**
 * CFRU-derived DexNav candidates. They are deliberately not added to MemoryMap:
 * Unbound may have moved them, so every result must be checked against the game.
 */
object DexNavProbe {
    const val STATE_POINTER_ADDRESS = 0x0203E038
    const val STATE_ADDRESS = 0x0203E051
    const val STATE_LENGTH = 0x37
    const val SEARCH_LEVELS_ADDRESS = 0x0203C75C
    private const val HUD_LENGTH = 28

    data class Hud(
        val speciesId: Int,
        val moves: List<Int>,
        val heldItemId: Int,
        val abilityId: Int,
        val potential: Int,
        val searchLevel: Int,
        val level: Int,
        val xProximity: Int,
        val yProximity: Int,
        val totalProximity: Int,
        val environment: Int,
        val tileX: Int,
        val tileY: Int,
    )

    data class Snapshot(
        val pointer: Int,
        val pointerLooksValid: Boolean,
        val chain: Int,
        val startedBattle: Boolean,
        val cooldown: Boolean,
        val lastSpeciesId: Int,
        val hud: Hud?,
        val rawState: ByteArray,
    )

    sealed class Result {
        data class Success(val snapshot: Snapshot) : Result()
        data class Failure(val message: String) : Result()
    }

    suspend fun read(client: RetroArchClient): Result {
        val pointerBytes = readBytes(client, STATE_POINTER_ADDRESS, 4)
            ?: return Result.Failure("Could not read candidate pointer at 0x${STATE_POINTER_ADDRESS.toString(16)}")
        val state = readBytes(client, STATE_ADDRESS, STATE_LENGTH)
            ?: return Result.Failure("Could not read candidate state at 0x${STATE_ADDRESS.toString(16)}")
        val pointer = u32(pointerBytes, 0)
        val validPointer = pointer in 0x02000000..0x0203FFFF
        val hudBytes = if (validPointer) readBytes(client, pointer, HUD_LENGTH) else null
        val hud = hudBytes?.takeIf { it.size >= HUD_LENGTH }?.let(::decodeHud)
        return Result.Success(
            Snapshot(
                pointer = pointer,
                pointerLooksValid = validPointer,
                chain = u8(state, 0),
                startedBattle = u8(state, 1) != 0,
                cooldown = u8(state, 0x0E) != 0,
                lastSpeciesId = u16(state, 0x35),
                hud = hud,
                rawState = state,
            ),
        )
    }

    private suspend fun readBytes(client: RetroArchClient, address: Int, length: Int): ByteArray? =
        when (val result = client.readCoreMemory(address, length)) {
            is RetroArchClient.Result.Success -> parseReadCoreMemoryResponse(result.response)
            is RetroArchClient.Result.Failure -> null
        }

    private fun decodeHud(bytes: ByteArray) = Hud(
        speciesId = u16(bytes, 0),
        moves = List(4) { u16(bytes, 2 + it * 2) },
        heldItemId = u16(bytes, 10),
        abilityId = u8(bytes, 12),
        potential = u8(bytes, 13),
        searchLevel = u8(bytes, 14),
        level = u8(bytes, 15),
        xProximity = u8(bytes, 17),
        yProximity = u8(bytes, 18),
        totalProximity = u8(bytes, 19),
        environment = u8(bytes, 20),
        tileX = s16(bytes, 24),
        tileY = s16(bytes, 26),
    )

    private fun u8(bytes: ByteArray, offset: Int) = bytes[offset].toInt() and 0xFF
    private fun u16(bytes: ByteArray, offset: Int) = u8(bytes, offset) or (u8(bytes, offset + 1) shl 8)
    private fun u32(bytes: ByteArray, offset: Int) =
        u8(bytes, offset) or (u8(bytes, offset + 1) shl 8) or (u8(bytes, offset + 2) shl 16) or (u8(bytes, offset + 3) shl 24)
    private fun s16(bytes: ByteArray, offset: Int) = u16(bytes, offset).toShort().toInt()
}
