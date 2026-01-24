package com.orliczspace.mesh_link.network.gateway

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class GatewayNatService(
    private val onInboundPacket: (ByteArray) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val socket = DatagramSocket()

    // Track active flows per mesh node
    private val activeFlows = ConcurrentHashMap<String, NatEntry>()

    fun handleOutbound(rawIpPacket: ByteArray, sourceNodeId: String) {
        val natEntry = NatEntry.createFromIpPacket(rawIpPacket)
        activeFlows[sourceNodeId] = natEntry // track this flow

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

                // remove from active flows once response delivered
                activeFlows.remove(sourceNodeId)
            } catch (e: Exception) {
                Log.e("GatewayNatService", "NAT error", e)
                activeFlows.remove(sourceNodeId)
            }
        }
    }

    fun getActiveFlows(): Map<String, NatEntry> {
        return activeFlows.toMap()
    }

    fun stop() {
        scope.cancel()
        socket.close()
        activeFlows.clear()
    }
}
