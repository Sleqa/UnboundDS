package com.unboundds.companion.pokemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3DecryptTest {

    /**
     * Builds a valid encrypted 100-byte struct from scratch (plaintext substructures
     * -> checksum -> XOR-encrypt in the order personality%24 dictates), so decode()
     * can be checked against known-good values instead of a real captured save.
     */
    private fun buildEncryptedStruct(
        personality: Long,
        otId: Long,
        nickname: String = "TESTMON",
        species: Int,
        heldItem: Int,
        experience: Long,
        friendship: Int,
        moves: IntArray,
        pp: IntArray,
    ): ByteArray {
        val struct = ByteArray(100)
        fun putU16(b: ByteArray, offset: Int, v: Int) {
            b[offset] = (v and 0xFF).toByte()
            b[offset + 1] = ((v shr 8) and 0xFF).toByte()
        }
        fun putU32(b: ByteArray, offset: Int, v: Long) {
            putU16(b, offset, (v and 0xFFFF).toInt())
            putU16(b, offset + 2, ((v shr 16) and 0xFFFF).toInt())
        }

        putU32(struct, 0x00, personality)
        putU32(struct, 0x04, otId)
        // nickname is plaintext Gen3-text at 0x08; encode 'A'-'Z' range only for simplicity
        val nickBytes = nickname.map { c -> (0xBB + (c - 'A')).toByte() }.toByteArray()
        nickBytes.copyInto(struct, 0x08)
        struct[0x08 + nickBytes.size] = 0xFF.toByte()

        // Build the 4 plaintext substructures (Growth/Attacks/EVs/Misc), 12 bytes each.
        val growth = ByteArray(12)
        putU16(growth, 0, species)
        putU16(growth, 2, heldItem)
        putU32(growth, 4, experience)
        growth[9] = friendship.toByte()

        val attacks = ByteArray(12)
        for (i in 0 until 4) putU16(attacks, i * 2, moves[i])
        for (i in 0 until 4) attacks[8 + i] = pp[i].toByte()

        val evs = ByteArray(12)
        val misc = ByteArray(12)

        val substructs = arrayOf(growth, attacks, evs, misc)

        // Determine order for this personality using the same table as Gen3Decrypt.
        val order = SUBSTRUCTURE_ORDER_FOR_TEST[(personality % 24).toInt()]
        val plainBlock = ByteArray(48)
        for (pos in 0 until 4) {
            substructs[order[pos]].copyInto(plainBlock, pos * 12)
        }

        // Checksum = sum of all 24 u16 words of the plaintext block.
        var sum = 0
        for (i in 0 until 24) {
            sum += (plainBlock[i * 2].toInt() and 0xFF) or ((plainBlock[i * 2 + 1].toInt() and 0xFF) shl 8)
        }
        putU16(struct, 0x1C, sum and 0xFFFF)

        // Encrypt: XOR each 32-bit word of the block with (personality xor otId).
        val key = personality xor otId
        for (i in 0 until 12) {
            val plainWord = (plainBlock[i * 4].toLong() and 0xFF) or
                ((plainBlock[i * 4 + 1].toLong() and 0xFF) shl 8) or
                ((plainBlock[i * 4 + 2].toLong() and 0xFF) shl 16) or
                ((plainBlock[i * 4 + 3].toLong() and 0xFF) shl 24)
            val encWord = plainWord xor key
            struct[0x20 + i * 4] = (encWord and 0xFF).toByte()
            struct[0x20 + i * 4 + 1] = ((encWord shr 8) and 0xFF).toByte()
            struct[0x20 + i * 4 + 2] = ((encWord shr 16) and 0xFF).toByte()
            struct[0x20 + i * 4 + 3] = ((encWord shr 24) and 0xFF).toByte()
        }

        return struct
    }

    @Test
    fun decodesAllTwentyFourSubstructureOrders() {
        for (personality in 0L until 24L) {
            val struct = buildEncryptedStruct(
                personality = personality,
                otId = 0x1234,
                species = 25, // Pikachu-ish id, arbitrary
                heldItem = 5,
                experience = 12345,
                friendship = 70,
                moves = intArrayOf(1, 2, 3, 4),
                pp = intArrayOf(10, 20, 30, 40),
            )
            val decoded = Gen3Decrypt.decode(struct)!!
            assertTrue("checksum should be valid for personality=$personality", decoded.checksumValid)
            assertEquals(25, decoded.speciesId)
            assertEquals(5, decoded.heldItemId)
            assertEquals(12345L, decoded.experience)
            assertEquals(70, decoded.friendship)
            assertEquals(listOf(1, 2, 3, 4), decoded.moves.toList())
            assertEquals(listOf(10, 20, 30, 40), decoded.pp.toList())
        }
    }

    @Test
    fun decodesNickname() {
        val struct = buildEncryptedStruct(
            personality = 7,
            otId = 99,
            nickname = "PIKA",
            species = 25,
            heldItem = 0,
            experience = 0,
            friendship = 0,
            moves = intArrayOf(0, 0, 0, 0),
            pp = intArrayOf(0, 0, 0, 0),
        )
        assertEquals("PIKA", Gen3Decrypt.decode(struct)!!.nickname)
    }

    @Test
    fun tooShortReturnsNull() {
        assertEquals(null, Gen3Decrypt.decode(ByteArray(10)))
    }

    companion object {
        // Mirrors Gen3Decrypt's private table (sourced from PKHeX's BlockPosition,
        // PKHeX.Core/PKM/Util/PokeCrypto.cs) so the test can build correctly-ordered fixtures.
        private val SUBSTRUCTURE_ORDER_FOR_TEST: Array<IntArray> = arrayOf(
            intArrayOf(0, 1, 2, 3), intArrayOf(0, 1, 3, 2), intArrayOf(0, 2, 1, 3), intArrayOf(0, 3, 1, 2),
            intArrayOf(0, 2, 3, 1), intArrayOf(0, 3, 2, 1), intArrayOf(1, 0, 2, 3), intArrayOf(1, 0, 3, 2),
            intArrayOf(2, 0, 1, 3), intArrayOf(3, 0, 1, 2), intArrayOf(2, 0, 3, 1), intArrayOf(3, 0, 2, 1),
            intArrayOf(1, 2, 0, 3), intArrayOf(1, 3, 0, 2), intArrayOf(2, 1, 0, 3), intArrayOf(3, 1, 0, 2),
            intArrayOf(2, 3, 0, 1), intArrayOf(3, 2, 0, 1), intArrayOf(1, 2, 3, 0), intArrayOf(1, 3, 2, 0),
            intArrayOf(2, 1, 3, 0), intArrayOf(3, 1, 2, 0), intArrayOf(2, 3, 1, 0), intArrayOf(3, 2, 1, 0),
        )
    }
}
