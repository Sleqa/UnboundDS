package com.unboundds.companion.memory

import com.unboundds.companion.network.RetroArchClient
import com.unboundds.companion.network.parseReadCoreMemoryResponse

/** A named GBA bus address range worth scanning (e.g. EWRAM, IWRAM). */
data class MemoryRegion(val name: String, val startAddress: Int, val length: Int)

object MemoryRegions {
    val EWRAM = MemoryRegion("EWRAM", 0x02000000, 0x40000)
    val IWRAM = MemoryRegion("IWRAM", 0x03000000, 0x8000)
    // CFRU-derived candidate window. Use the DexNav probe to validate it first.
    val DexNavCandidate = MemoryRegion("DexNav candidate", 0x0203E038, 0x60)
    // CFRU/FireRed SaveBlock1 storage candidate. The pointer is still preferred;
    // this gives the scanner a useful fallback when Unbound's pointer differs.
    val SaveBlock1Candidate = MemoryRegion("SaveBlock1 candidate", 0x0202552C, 0x2000)
    val all = listOf(EWRAM, IWRAM, DexNavCandidate, SaveBlock1Candidate)
}

/**
 * Snapshot-and-diff memory scanner: read a region, let the caller perform one
 * controlled action in-game, read the region again, and report which byte
 * ranges changed. This is the discovery workflow for finding unknown Unbound
 * RAM addresses — e.g. snapshot EWRAM, spend money in-game, compare, and the
 * changed bytes are very likely the money value.
 */
class DiffScanner(private val client: RetroArchClient) {
    private companion object {
        // Kept well under the UDP receive buffer once hex-encoded (2 chars/byte).
        const val CHUNK_SIZE = 2048
    }

    sealed class ScanResult {
        data class Success(val bytes: ByteArray) : ScanResult()
        data class Failure(val message: String) : ScanResult()
    }

    data class Diff(val address: Int, val before: ByteArray, val after: ByteArray)

    suspend fun readRegion(region: MemoryRegion): ScanResult {
        val out = ByteArray(region.length)
        var offset = 0
        while (offset < region.length) {
            val len = minOf(CHUNK_SIZE, region.length - offset)
            when (val result = client.readCoreMemory(region.startAddress + offset, len)) {
                is RetroArchClient.Result.Success -> {
                    val bytes = parseReadCoreMemoryResponse(result.response)
                        ?: return ScanResult.Failure(
                            "Read rejected at offset 0x${offset.toString(16)} — is a game loaded?",
                        )
                    bytes.copyInto(out, offset)
                }
                is RetroArchClient.Result.Failure -> return ScanResult.Failure(result.message)
            }
            offset += len
        }
        return ScanResult.Success(out)
    }

    /** Groups changed bytes into contiguous runs so e.g. a 4-byte value shows as one diff, not four. */
    fun diff(before: ByteArray, after: ByteArray, baseAddress: Int): List<Diff> {
        require(before.size == after.size) { "Snapshot size mismatch — re-scan both." }
        val changed = changedOffsets(before, after)
        return group(changed, before, after, baseAddress)
    }

    /** Offsets (into the snapshot arrays) whose byte differs between [before] and [after]. */
    fun changedOffsets(before: ByteArray, after: ByteArray): Set<Int> {
        require(before.size == after.size) { "Snapshot size mismatch — re-scan both." }
        val out = mutableSetOf<Int>()
        for (i in before.indices) if (before[i] != after[i]) out += i
        return out
    }

    /**
     * Like [diff], but excludes any offset in [noiseOffsets] — addresses that were
     * already found to change with no player action (RNG, animation/frame counters,
     * audio state, etc). Massively cuts down real diffs on a large region like EWRAM.
     */
    fun diffExcludingNoise(
        before: ByteArray,
        after: ByteArray,
        baseAddress: Int,
        noiseOffsets: Set<Int>,
    ): List<Diff> {
        val changed = changedOffsets(before, after) - noiseOffsets
        return group(changed, before, after, baseAddress)
    }

    private fun group(offsets: Set<Int>, before: ByteArray, after: ByteArray, baseAddress: Int): List<Diff> {
        if (offsets.isEmpty()) return emptyList()
        val sorted = offsets.sorted()
        val diffs = mutableListOf<Diff>()
        var runStart = sorted[0]
        var runEnd = sorted[0]
        for (offset in sorted.drop(1)) {
            if (offset == runEnd + 1) {
                runEnd = offset
            } else {
                diffs += Diff(
                    address = baseAddress + runStart,
                    before = before.copyOfRange(runStart, runEnd + 1),
                    after = after.copyOfRange(runStart, runEnd + 1),
                )
                runStart = offset
                runEnd = offset
            }
        }
        diffs += Diff(
            address = baseAddress + runStart,
            before = before.copyOfRange(runStart, runEnd + 1),
            after = after.copyOfRange(runStart, runEnd + 1),
        )
        return diffs
    }
}
