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
        private const val DEDUP_WINDOW_MS = 10_000L
    }

    /** 🔴 Injected AFTER construction */
    lateinit var meshNetworkManager: MeshNetworkManager

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val seenPackets = ConcurrentHashMap<String, Long>()

    interface PacketDeliveryListener {
        fun onPacketDelivered(payload: ByteArray)
    }

    var deliveryListener: PacketDeliveryListener? = null

    suspend fun forward(packet: MeshPacket) {
        if (packet.ttl <= 0) return

        if (packet.type == PacketType.NAT_FORWARD) {
            gatewayNatService.handleOutbound(packet.payload, packet.sourceNodeId)
            return
        }

        val nextHop = packet.destinationNodeId
            ?.let { routingRepository.routingTable[it] }
            ?: return

        if (packet.destinationNodeId != null) {
            meshNetworkManager.sendSecurePacket(
                packet.destinationNodeId,
                packet.payload,
                packet.type
            )
            return
        }

        val target = InetSocketAddress(nextHop.ip, nextHop.port)
        socket.send(DatagramPacket(packet.payload, packet.payload.size, target))
    }

    fun onDatagramReceived(data: ByteArray, length: Int, sender: InetAddress, port: Int) {
        try {
            val packet = MeshPacket.deserialize(data, length)

            if (isDuplicate(packet.packetId)) return

            if (packet.destinationNodeId == android.os.Build.MODEL) {
                deliveryListener?.onPacketDelivered(packet.payload)
                return
            }

            scope.launch {
                forward(packet.copy(ttl = packet.ttl - 1))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Datagram error", e)
        }
    }

    private fun isDuplicate(id: String): Boolean {
        val now = System.currentTimeMillis()
        seenPackets.entries.removeIf { now - it.value > DEDUP_WINDOW_MS }
        return seenPackets.putIfAbsent(id, now) != null
    }

    fun getActiveInternetFlows(): Map<String, NatEntry> =
        gatewayNatService.getActiveFlows()
}
