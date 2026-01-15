package com.orliczspace.mesh_link.network.gateway

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class GatewayNatService(
    private val onInboundPacket: (ByteArray) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val socket = DatagramSocket()

    fun handleOutbound(rawIpPacket: ByteArray) {
        val natEntry = NatEntry.createFromIpPacket(rawIpPacket)

        scope.launch {
            try {
                // send to real destination
                val outgoing = DatagramPacket(
                    natEntry.payload,
                    natEntry.payload.size,
                    InetAddress.getByName(natEntry.destIp),
                    natEntry.destPort
                )
                socket.send(outgoing)

                // receive response
                val buffer = ByteArray(65535)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                val responsePayload = response.data.copyOf(response.length)

                // rebuild packet for return to VPN/mesh
                val rebuiltPacket = IpUdpPacketBuilder.buildResponse(
                    natEntry,
                    responsePayload
                )
                onInboundPacket(rebuiltPacket)
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
