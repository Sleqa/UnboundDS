package com.unboundds.companion.memory

import com.unboundds.companion.network.RetroArchClient
import org.junit.Assert.assertEquals
import org.junit.Test

class DiffScannerTest {

    private val scanner = DiffScanner(RetroArchClient())

    @Test
    fun groupsContiguousChangedBytesIntoOneDiff() {
        val before = byteArrayOf(1, 2, 3, 4, 5)
        val after = byteArrayOf(1, 9, 9, 9, 5)
        val diffs = scanner.diff(before, after, baseAddress = 0x1000)

        assertEquals(1, diffs.size)
        assertEquals(0x1001, diffs[0].address)
        assertEquals(3, diffs[0].before.size)
    }

    @Test
    fun separatesNonContiguousChanges() {
        val before = byteArrayOf(1, 2, 3, 4, 5)
        val after = byteArrayOf(9, 2, 3, 4, 9)
        val diffs = scanner.diff(before, after, baseAddress = 0)

        assertEquals(2, diffs.size)
        assertEquals(0, diffs[0].address)
        assertEquals(4, diffs[1].address)
    }

    @Test
    fun noiseFilteringExcludesKnownChangingBytes() {
        val before = byteArrayOf(1, 2, 3)
        val after = byteArrayOf(1, 9, 8)
        val noise = setOf(2) // offset 2 (value 3->8) is known noise, offset 1 is not

        val diffs = scanner.diffExcludingNoise(before, after, baseAddress = 0, noiseOffsets = noise)

        assertEquals(1, diffs.size)
        assertEquals(1, diffs[0].address)
    }

    @Test
    fun noDifferencesReturnsEmptyList() {
        val bytes = byteArrayOf(1, 2, 3)
        assertEquals(emptyList<DiffScanner.Diff>(), scanner.diff(bytes, bytes.copyOf(), 0))
    }
}
