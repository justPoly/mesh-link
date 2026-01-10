package com.orliczspace.mesh_link.network.gateway

import android.util.Log
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer

class GatewayNatService(
    private val writeBack: (ByteArray) -> Unit
) {

    companion object {
        private const val TAG = "GatewayNAT"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun handleIpPacket(packet: ByteArray) {
        scope.launch {
            try {
                val destIp = parseDestinationIp(packet)
                if (destIp == null) {
                    Log.w(TAG, "Invalid IP packet")
                    return@launch
                }

                Log.d(TAG, "Forwarding packet to $destIp")

                forwardViaTcp(destIp, packet)

            } catch (e: Exception) {
                Log.e(TAG, "NAT error: ${e.message}")
            }
        }
    }

    /**
     * VERY BASIC IPv4 destination parsing
     */
    private fun parseDestinationIp(packet: ByteArray): String? {
        if (packet.size < 20) return null

        val buffer = ByteBuffer.wrap(packet)
        buffer.position(16)

        val ip = ByteArray(4)
        buffer.get(ip)

        return ip.joinToString(".") { (it.toInt() and 0xFF).toString() }
    }

    /**
     * Simplified TCP forward (HTTP/HTTPS will work)
     */
    private fun forwardViaTcp(destIp: String, packet: ByteArray) {
        val socket = Socket()

        try {
            socket.connect(InetSocketAddress(destIp, 80), 5_000)

            val output = socket.getOutputStream()
            val input = socket.getInputStream()

            output.write(packet)
            output.flush()

            val response = input.readBytes()

            writeBack(response)

            Log.d(TAG, "Response returned (${response.size} bytes)")

        } finally {
            socket.close()
        }
    }

    fun stop() {
        scope.cancel()
    }
}
