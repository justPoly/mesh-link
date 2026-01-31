package com.orliczspace.mesh_link.network

import android.util.Log
import com.orliczspace.mesh_link.network.gateway.GatewayNatService
import com.orliczspace.mesh_link.network.packet.MeshPacket
import com.orliczspace.mesh_link.network.packet.PacketType
import com.orliczspace.mesh_link.network.security.CryptoManager
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
        private const val DEDUP_WINDOW_MS = 10_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Packet de-duplication cache
    private val seenPackets = ConcurrentHashMap<String, Long>()

    interface PacketDeliveryListener {
        fun onPacketDelivered(payload: ByteArray)
    }

    var deliveryListener: PacketDeliveryListener? = null

    /**
     * Forward a packet to the next hop
     */
    suspend fun forward(packet: MeshPacket) {
        if (packet.ttl <= 0) {
            Log.d(TAG, "Packet dropped: TTL expired")
            return
        }

        // NAT forwarding handled separately
        if (packet.type == PacketType.NAT_FORWARD) {
            gatewayNatService.handleOutbound(
                packet.payload,
                packet.sourceNodeId
            )
            return
        }

        val nextHop = packet.destinationNodeId
            ?.let { routingRepository.routingTable[it] }
            ?: run {
                Log.w(TAG, "No route to destination ${packet.destinationNodeId}")
                return
            }

        val encoded = ForwardPacketCodec.encode(packet)
        val encrypted = CryptoManager.encrypt(encoded)

        val datagram = DatagramPacket(
            encrypted,
            encrypted.size,
            InetSocketAddress(nextHop.ip, nextHop.port)
        )

        socket.send(datagram)

        Log.d(TAG, "Packet forwarded to ${nextHop.ip}:${nextHop.port}")
    }

    /**
     * Called when a UDP datagram is received
     */
    fun onDatagramReceived(
        data: ByteArray,
        length: Int,
        sender: InetAddress,
        port: Int
    ) {
        try {
            val decrypted = CryptoManager.decrypt(data.copyOf(length))
            val packet = ForwardPacketCodec.decode(decrypted)

            // Drop duplicate packets
            if (isDuplicate(packet.packetId)) {
                Log.d(TAG, "Duplicate packet dropped: ${packet.packetId}")
                return
            }

            // Packet is for this node
            if (packet.destinationNodeId == android.os.Build.MODEL) {
                deliveryListener?.onPacketDelivered(packet.payload)
                return
            }

            // Forward packet with decremented TTL
            scope.launch {
                forward(packet.copy(ttl = packet.ttl - 1))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process incoming packet", e)
        }
    }

    /**
     * Packet de-duplication logic
     */
    private fun isDuplicate(id: String): Boolean {
        val now = System.currentTimeMillis()

        // Cleanup old entries
        seenPackets.entries.removeIf { now - it.value > DEDUP_WINDOW_MS }

        // Returns true if packet already exists
        return seenPackets.putIfAbsent(id, now) != null
    }
}
