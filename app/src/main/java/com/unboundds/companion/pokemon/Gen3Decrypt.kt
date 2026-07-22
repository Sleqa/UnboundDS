package com.unboundds.companion.pokemon

/**
 * Decrypts the 48-byte encrypted data block (offset 0x20-0x4F) of a Gen 3
 * Pokemon struct to extract species, held item, experience, and moves.
 *
 * The block is split into 4 x 12-byte substructures (Growth/Attacks/EVs/Misc)
 * whose ORDER depends on `personality % 24`, then each of the block's 12
 * 32-bit words is XORed with `personality XOR otId`.
 *
 * checksumValid mirrors vanilla FireRed's own struct checksum (offset 0x1C).
 * On Pokemon Unbound specifically it reads FALSE even when species/nickname/
 * moves are independently confirmed correct against real hardware (verified
 * 2026-07-22 across a full party + enemy team) -- Unbound's engine appears to
 * compute the checksum differently (likely folding in data for its custom
 * Mega Evolution/mission systems) without changing the substructure layout.
 * Treat checksumValid as informational on Unbound, not a correctness gate.
 */
object Gen3Decrypt {
    // Row i = substructure order for personality % 24 == i.
    // Values are substructure identities (0=Growth 1=Attacks 2=EVs 3=Misc) in
    // position order, e.g. row 0 = [0,1,2,3] means position0=Growth, position1=Attacks...
    // Sourced from PKHeX's BlockPosition table (PKHeX.Core/PKM/Util/PokeCrypto.cs,
    // kwsch/PKHeX) 2026-07-22 -- a previous hand-transcribed version of this table had
    // several rows wrong (rows 8-13, 15, 17-20, 22 in particular), which correctly
    // decoded species/nickname (position 0 = Growth was still right for many personality
    // values) while corrupting moves/PP for Pokemon whose personality%24 hit a wrong row.
    private val SUBSTRUCTURE_ORDER: Array<IntArray> = arrayOf(
        intArrayOf(0, 1, 2, 3), intArrayOf(0, 1, 3, 2), intArrayOf(0, 2, 1, 3), intArrayOf(0, 3, 1, 2),
        intArrayOf(0, 2, 3, 1), intArrayOf(0, 3, 2, 1), intArrayOf(1, 0, 2, 3), intArrayOf(1, 0, 3, 2),
        intArrayOf(2, 0, 1, 3), intArrayOf(3, 0, 1, 2), intArrayOf(2, 0, 3, 1), intArrayOf(3, 0, 2, 1),
        intArrayOf(1, 2, 0, 3), intArrayOf(1, 3, 0, 2), intArrayOf(2, 1, 0, 3), intArrayOf(3, 1, 0, 2),
        intArrayOf(2, 3, 0, 1), intArrayOf(3, 2, 0, 1), intArrayOf(1, 2, 3, 0), intArrayOf(1, 3, 2, 0),
        intArrayOf(2, 1, 3, 0), intArrayOf(3, 1, 2, 0), intArrayOf(2, 3, 1, 0), intArrayOf(3, 2, 1, 0),
    )

    data class Decoded(
        val nickname: String,
        val otName: String,
        val checksumValid: Boolean,
        val speciesId: Int,
        val heldItemId: Int,
        val experience: Long,
        val friendship: Int,
        val moves: IntArray,
        val pp: IntArray,
    )

    /** [struct] must be at least 0x50 bytes (the shared Box+party prefix). */
    fun decode(struct: ByteArray): Decoded? {
        if (struct.size < 0x50) return null

        val personality = struct.u32(0x00)
        val otId = struct.u32(0x04)
        val nickname = Gen3Text.decode(struct.copyOfRange(0x08, 0x08 + 10))
        val otName = Gen3Text.decode(struct.copyOfRange(0x14, 0x14 + 7))
        val storedChecksum = struct.u16(0x1C)

        val key = personality xor otId
        val decrypted = ByteArray(48)
        for (i in 0 until 12) {
            val word = struct.u32(0x20 + i * 4) xor key
            decrypted[i * 4] = (word and 0xFF).toByte()
            decrypted[i * 4 + 1] = ((word shr 8) and 0xFF).toByte()
            decrypted[i * 4 + 2] = ((word shr 16) and 0xFF).toByte()
            decrypted[i * 4 + 3] = ((word shr 24) and 0xFF).toByte()
        }

        var sum = 0
        for (i in 0 until 24) sum += decrypted.u16(i * 2)
        val checksumValid = (sum and 0xFFFF) == storedChecksum

        val order = SUBSTRUCTURE_ORDER[(personality % 24).toInt()]
        val offsetOf = IntArray(4)
        for (pos in 0 until 4) offsetOf[order[pos]] = pos * 12
        val growth = offsetOf[0]
        val attacks = offsetOf[1]

        return Decoded(
            nickname = nickname,
            otName = otName,
            checksumValid = checksumValid,
            speciesId = decrypted.u16(growth),
            heldItemId = decrypted.u16(growth + 2),
            experience = decrypted.u32(growth + 4),
            friendship = decrypted[growth + 9].toInt() and 0xFF,
            moves = intArrayOf(
                decrypted.u16(attacks), decrypted.u16(attacks + 2),
                decrypted.u16(attacks + 4), decrypted.u16(attacks + 6),
            ),
            pp = intArrayOf(
                decrypted[attacks + 8].toInt() and 0xFF, decrypted[attacks + 9].toInt() and 0xFF,
                decrypted[attacks + 10].toInt() and 0xFF, decrypted[attacks + 11].toInt() and 0xFF,
            ),
        )
    }

    private fun ByteArray.u16(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.u32(offset: Int): Long =
        u16(offset).toLong() or (u16(offset + 2).toLong() shl 16)
}
