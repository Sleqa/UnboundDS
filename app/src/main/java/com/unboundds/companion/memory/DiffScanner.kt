package com.unboundds.companion.memory

import com.unboundds.companion.network.RetroArchClient
import com.unboundds.companion.network.parseReadCoreMemoryResponse

/** A named GBA bus address range worth scanning (e.g. EWRAM, IWRAM). */
data class MemoryRegion(val name: String, val startAddress: Int, val length: Int)

object MemoryRegions {
    val EWRAM = MemoryRegion("EWRAM", 0x02000000, 0x40000)
    val IWRAM = MemoryRegion("IWRAM", 0x03000000, 0x8000)
    val all = listOf(EWRAM, IWRAM)
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
        val diffs = mutableListOf<Diff>()
        var i = 0
        while (i < before.size) {
            if (before[i] != after[i]) {
                val start = i
                while (i < before.size && before[i] != after[i]) i++
                diffs += Diff(
                    address = baseAddress + start,
                    before = before.copyOfRange(start, i),
                    after = after.copyOfRange(start, i),
                )
            } else {
                i++
            }
        }
        return diffs
    }
}
