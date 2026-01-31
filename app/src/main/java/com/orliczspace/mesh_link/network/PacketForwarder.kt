package com.orliczspace.mesh_link.network

import android.util.Log
import com.orliczspace.mesh_link.network.gateway.GatewayNatService
import com.orliczspace.mesh_link.network.gateway.NatEntry
import com.orliczspace.mesh_link.network.packet.MeshPacket
import com.orliczspace.mesh_link.network.packet.PacketType
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

class PacketForwarder(
    private val socket: DatagramSocket,
    private val routingRepository: RoutingStateRepository,
    private val gatewayNatService: GatewayNatService
) {

    companion object {
        private const val TAG = "PacketForwarder"
        private const val MAX_RETRIES = 3
        private const val ACK_TIMEOUT_MS = 1500L
    }

    /** packetId → ACK waiter */
    private val pendingAcks = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Callback to deliver payload to local app */
    interface PacketDeliveryListener {
        fun onPacketDelivered(payload: ByteArray)
    }

    var deliveryListener: PacketDeliveryListener? = null

    /* -------------------------------------------------------------
     * OUTGOING
     * ------------------------------------------------------------- */

    suspend fun forwardPacket(
        sourceNodeId: String,
        packet: MeshPacket
    ) {
        if (packet.ttl <= 0) {
            Log.w(TAG, "Dropped packet ${packet.packetId}: TTL expired")
            return
        }

        // Handle NAT-bound packets
        if (packet.type == PacketType.NAT_FORWARD) {
            gatewayNatService.handleOutbound(packet.serialize(), sourceNodeId)
            return
        }

        // Determine next hop
        val nextHopNode = packet.destinationNodeId?.let { routingRepository.routingTable[it] }
        if (nextHopNode == null) {
            Log.e(TAG, "No route to ${packet.destinationNodeId}")
            return
        }

        val target = InetSocketAddress(nextHopNode.ip, nextHopNode.port)
        sendWithReliability(packet, target)
    }

    private suspend fun sendWithReliability(packet: MeshPacket, target: InetSocketAddress) {
        repeat(MAX_RETRIES) { attempt ->
            Log.d(TAG, "Sending ${packet.packetId}, attempt ${attempt + 1}")

            val ackDeferred = CompletableDeferred<Boolean>()
            pendingAcks[packet.packetId] = ackDeferred

            sendRaw(packet, target)

            val ackReceived = withTimeoutOrNull(ACK_TIMEOUT_MS) { ackDeferred.await() }
            if (ackReceived == true) {
                Log.d(TAG, "ACK received for ${packet.packetId}")
                pendingAcks.remove(packet.packetId)
                return
            }

            Log.w(TAG, "ACK timeout for ${packet.packetId}")
        }

        pendingAcks.remove(packet.packetId)
        Log.e(TAG, "Delivery failed: ${packet.packetId}")
    }

    private fun sendRaw(packet: MeshPacket, target: InetSocketAddress) {
        val bytes = packet.serialize()
        val datagram = DatagramPacket(bytes, bytes.size, target.address, target.port)
        socket.send(datagram)
    }

    /* -------------------------------------------------------------
     * INCOMING
     * ------------------------------------------------------------- */

    fun onDatagramReceived(data: ByteArray, length: Int, sender: InetAddress, senderPort: Int) {
        val packet = MeshPacket.deserialize(data, length)
        handleIncomingPacket(packet, sender, senderPort)
    }

    private fun handleIncomingPacket(packet: MeshPacket, sender: InetAddress, senderPort: Int) {
        when (packet.type) {
            PacketType.ACK -> handleAck(packet)
            PacketType.DATA -> handleIncomingData(packet, sender, senderPort)
            PacketType.NAT_FORWARD -> gatewayNatService.handleInbound(packet.serialize())
            else -> {
                Log.w(TAG, "Unhandled packet type ${packet.type}")
            }
        }
    }

    private fun handleAck(packet: MeshPacket) {
        packet.ackForPacketId?.let { ackedId ->
            pendingAcks[ackedId]?.complete(true)
        }
    }

    private fun handleIncomingData(packet: MeshPacket, sender: InetAddress, senderPort: Int) {
        if (packet.requiresAck) {
            sendAck(packet, sender, senderPort)
        }

        // Deliver to local node if destination matches
        if (packet.destinationNodeId == android.os.Build.MODEL) {
            Log.d(TAG, "Delivered locally: ${packet.packetId}")
            deliveryListener?.onPacketDelivered(packet.payload)
            return
        }

        // Forward packet with TTL decremented
        val forwarded = packet.copy(ttl = packet.ttl - 1)
        scope.launch { forwardPacket(forwarded.sourceNodeId, forwarded) }
    }

    private fun sendAck(packet: MeshPacket, sender: InetAddress, senderPort: Int) {
        // Use the local device model as the sourceNodeId
        val sourceNodeId = android.os.Build.MODEL ?: "unknown-node"

        // Create ACK packet with correct sourceNodeId
        val ack = MeshPacket.createAck(sourceNodeId = sourceNodeId, packetId = packet.packetId)

        // Convert to DatagramPacket and send
        val bytes = ack.serialize()
        val datagram = DatagramPacket(bytes, bytes.size, sender, senderPort)
        socket.send(datagram)
    }

    /** Get active internet flows (stub for VPN UI) */
    fun getActiveInternetFlows(): Map<String, NatEntry> {
        return gatewayNatService.getActiveFlows()
    }
}
