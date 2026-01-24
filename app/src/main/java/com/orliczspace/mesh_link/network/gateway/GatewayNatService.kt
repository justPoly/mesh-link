package com.orliczspace.mesh_link.network.gateway

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles NAT for internet-bound packets in the mesh network.
 * Tracks active flows and ensures return packets reach the originating node.
 */
class GatewayNatService(
    private val flowLogger: FlowLogger,
    private val onInboundPacket: (ByteArray) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val socket = DatagramSocket()

    // Track active flows: sourceNodeId -> NatEntry
    private val activeFlows = ConcurrentHashMap<String, NatEntry>()

    /**
     * Handle outbound internet-bound packet from a mesh node.
     */
    fun handleOutbound(rawIpPacket: ByteArray, sourceNodeId: String?) {
        val natEntry = NatEntry.createFromIpPacket(rawIpPacket, sourceNodeId)
        val nodeKey = sourceNodeId ?: natEntry.sourceNodeId ?: return

        activeFlows[nodeKey] = natEntry
        flowLogger.logOutboundFlow(nodeKey, natEntry)

        scope.launch {
            try {
                // Send packet to actual destination
                val outgoing = DatagramPacket(
                    natEntry.payload,
                    natEntry.payload.size,
                    InetAddress.getByName(natEntry.destIp),
                    natEntry.destPort
                )
                socket.send(outgoing)

                // Wait for response
                val buffer = ByteArray(65535)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                val responsePayload = response.data.copyOf(response.length)

                // Rebuild packet for VPN/TUN
                val rebuiltPacket = IpUdpPacketBuilder.buildResponse(natEntry, responsePayload)
                onInboundPacket(rebuiltPacket)

                // Mark flow ended
                activeFlows.remove(nodeKey)
                flowLogger.markFlowEnded(nodeKey)

            } catch (e: Exception) {
                Log.e("GatewayNatService", "NAT error for node $nodeKey", e)
                activeFlows.remove(nodeKey)
                flowLogger.markFlowEnded(nodeKey)
            }
        }
    }

    /** Return snapshot of current active flows */
    fun getActiveFlows(): Map<String, NatEntry> = activeFlows.toMap()

    /** Stop service and clean up */
    fun stop() {
        scope.cancel()
        socket.close()
        activeFlows.clear()
    }
}
