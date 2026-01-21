package com.orliczspace.mesh_link.network.gateway

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

data class NatKey(val srcIp: String, val srcPort: Int, val destIp: String, val destPort: Int)

class GatewayNatService(
    private val onInboundPacket: (ByteArray) -> Unit
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val socket = DatagramSocket()

    // NAT table maps destination (external) to the original mesh sender
    private val natTable = ConcurrentHashMap<NatKey, String>() // NatKey -> sourceNodeId

    /**
     * Handles outbound packet from mesh toward internet.
     * Tracks the source node in NAT table for correct response routing.
     */
    fun handleOutbound(rawIpPacket: ByteArray, sourceNodeId: String) {
        val natEntry = NatEntry.createFromIpPacket(rawIpPacket)

        // Save mapping in NAT table
        val key = NatKey(natEntry.srcIp, natEntry.srcPort, natEntry.destIp, natEntry.destPort)
        natTable[key] = sourceNodeId

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

                // lookup original sender from NAT table
                val reverseKey = NatKey(
                    srcIp = natEntry.destIp,
                    srcPort = natEntry.destPort,
                    destIp = natEntry.srcIp,
                    destPort = natEntry.srcPort
                )
                val originalNode = natTable.remove(reverseKey) // remove after use

                if (originalNode != null) {
                    // rebuild packet to send back to mesh/VPN
                    val rebuiltPacket = IpUdpPacketBuilder.buildResponse(natEntry, responsePayload)
                    onInboundPacket(rebuiltPacket)
                    Log.d("GatewayNatService", "Inbound packet delivered to $originalNode")
                } else {
                    Log.w("GatewayNatService", "No NAT entry for response packet")
                }
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
