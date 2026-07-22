package com.unboundds.companion.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Thin client for RetroArch's UDP Network Command Interface
 * (Settings > Network > Network Commands, default port 55355).
 */
class RetroArchClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 55355,
    private val timeoutMs: Int = 1000,
) {
    sealed class Result {
        data class Success(val response: String) : Result()
        data class Failure(val message: String) : Result()
    }

    suspend fun sendCommand(command: String): Result = withContext(Dispatchers.IO) {
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val address = InetAddress.getByName(host)
                val requestBytes = "$command\n".toByteArray(Charsets.US_ASCII)
                socket.send(DatagramPacket(requestBytes, requestBytes.size, address, port))

                val buffer = ByteArray(8192)
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                Result.Success(String(packet.data, 0, packet.length, Charsets.US_ASCII).trim())
            }
        } catch (e: SocketTimeoutException) {
            Result.Failure("Timed out — is RetroArch running with Network Commands enabled on port $port?")
        } catch (e: Exception) {
            Result.Failure(e.message ?: e.toString())
        }
    }

    suspend fun getVersion(): Result = sendCommand("VERSION")

    /** Reads [length] bytes from GBA bus address [address] (e.g. 0x02000000 for EWRAM). */
    suspend fun readCoreMemory(address: Int, length: Int): Result =
        sendCommand("READ_CORE_MEMORY ${address.toString(16)} $length")
}

/**
 * Parses a "READ_CORE_MEMORY <addr> <hex bytes...>" reply into raw bytes.
 * Returns null if the core rejected the read (RetroArch replies with "-1").
 */
fun parseReadCoreMemoryResponse(response: String): ByteArray? {
    val parts = response.trim().split(Regex("\\s+"))
    if (parts.size < 3 || parts[0] != "READ_CORE_MEMORY") return null
    val hexBytes = parts.drop(2)
    if (hexBytes.size == 1 && hexBytes[0] == "-1") return null
    return try {
        ByteArray(hexBytes.size) { i -> hexBytes[i].toInt(16).toByte() }
    } catch (e: NumberFormatException) {
        null
    }
}
