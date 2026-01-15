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

                val responsePayload = response.data.copyOf(response.length)

                onInboundPacket(natEntry, responsePayload)

            } catch (e: Exception) {
                Log.e("GatewayNatService", "NAT error", e)
            }
        }
    }

    fun stop() {
        scope.cancel()
        socket.close()
    }
}
