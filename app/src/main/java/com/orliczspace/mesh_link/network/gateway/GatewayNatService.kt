package com.orliczspace.mesh_link.network.gateway

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class GatewayNatService(
    private val onInboundPacket: (NatEntry, ByteArray) -> Unit
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val socket = DatagramSocket()

    /**
     * Handles an outbound raw IP packet coming from the mesh gateway.
     * This sends it to the real internet and waits for a response.
     */
    fun handleOutbound(rawIpPacket: ByteArray) {
        val natEntry = NatEntry.createFromIpPacket(rawIpPacket)

        scope.launch {
            try {
                val outgoing = DatagramPacket(
                    natEntry.payload,
                    natEntry.payload.size,
                    InetAddress.getByName(natEntry.destIp),
                    natEntry.destPort
                )

                socket.send(outgoing)

                val buffer = ByteArray(65535)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)

                onInboundPacket(
                    natEntry,
                    response.data.copyOf(response.length)
                )

            } catch (e: Exception) {
                Log.e("GatewayNatService", "NAT handling failed", e)
            }
        }
    }

    fun stop() {
        scope.cancel()
        socket.close()
    }
}
