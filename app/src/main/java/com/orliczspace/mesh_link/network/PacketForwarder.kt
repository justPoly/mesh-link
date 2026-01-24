package com.orliczspace.mesh_link.network

import android.util.Log
import com.orliczspace.mesh_link.network.gateway.GatewayNatService
import com.orliczspace.mesh_link.network.gateway.NatEntry
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class PacketForwarder(
    private val localNodeId: String,
    private val routingRepository: RoutingStateRepository,
    private val linkProbeService: LinkProbeService,
    private val listenPort: Int = 9999,
    private val deliveryListener: PacketDeliveryListener
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null

    // NAT service handles outbound packets and reconstructs return packets
    private val gatewayNatService = GatewayNatService { inboundPacket: ByteArray ->
        deliveryListener.onPacketDelivered(inboundPacket)
    }

    /* ---------------- LIFECYCLE ---------------- */

    fun start() {
        socket = DatagramSocket(listenPort)
        listen()
        Log.d("PacketForwarder", "Started on port $listenPort")
    }

    fun stop() {
        scope.cancel()
        socket?.close()
        socket = null
        Log.d("PacketForwarder", "Stopped")
    }

    /* ---------------- RECEIVE ---------------- */

    private fun listen() {
        scope.launch {
            val buffer = ByteArray(65535)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    handlePacket(packet)
                } catch (e: Exception) {
                    if (isActive) Log.e("PacketForwarder", "Receive error: ${e.message}")
                }
            }
        }
    }

    private fun handlePacket(packet: DatagramPacket) {
        val forwardPacket = ForwardPacketCodec.decode(packet.data, packet.length)

        if (forwardPacket.ttl <= 0) {
            Log.d("PacketForwarder", "Dropped packet (TTL expired)")
            return
        }

        val route = routingRepository.getRouteToInternet(localNodeId)

        // Packet destined to this node
        if (forwardPacket.destinationNodeId == localNodeId) {
            onPacketDelivered(forwardPacket)
            return
        }

        // Internet-bound packet and this node is gateway
        if (forwardPacket.destinationNodeId == null &&
            route != null && !route.viaGateway
        ) {
            gatewayNatService.handleOutbound(forwardPacket.payload, forwardPacket.sourceNodeId)
            return
        }

        // Forward to next hop in mesh
        forward(forwardPacket)
    }

    /* ---------------- FORWARD ---------------- */

    fun forwardRawIpPacket(rawPacket: ByteArray) {
        val forwardPacket = ForwardPacket(
            sourceNodeId = localNodeId,
            destinationNodeId = null,
            ttl = 8,
            payload = rawPacket
        )
        forward(forwardPacket)
    }

    // 🔹 Make send() publicly callable
    fun send(packet: ForwardPacket) {
        forward(packet)
    }

    private fun forward(packet: ForwardPacket) {
        val route = routingRepository.getRouteToInternet(localNodeId)
        if (route == null) {
            Log.d("PacketForwarder", "No route available, dropping packet")
            return
        }

        // Deliver locally if this node is the gateway
        if (!route.viaGateway) {
            onPacketDelivered(packet)
            return
        }

        val nextHopNodeId = route.nextHopNodeId ?: return
        val nextHopIp = linkProbeService.getKnownPeers()[nextHopNodeId] ?: run {
            Log.d("PacketForwarder", "Next hop IP unknown for $nextHopNodeId")
            return
        }

        val forwardedPacket = packet.copy(ttl = packet.ttl - 1)
        val data = ForwardPacketCodec.encode(forwardedPacket)

        scope.launch {
            try {
                socket?.send(
                    DatagramPacket(data, data.size, InetAddress.getByName(nextHopIp), listenPort)
                )
                Log.d("PacketForwarder", "Forwarded packet to $nextHopNodeId @ $nextHopIp")
            } catch (e: Exception) {
                Log.e("PacketForwarder", "Forward error: ${e.message}")
            }
        }
    }

    /* ---------------- DELIVERY ---------------- */

    private fun onPacketDelivered(packet: ForwardPacket) {
        Log.d(
            "PacketForwarder",
            "Packet delivered from ${packet.sourceNodeId} (${packet.payload.size} bytes)"
        )
        deliveryListener.onPacketDelivered(packet.payload)
    }

    // 🔹 Return Map<String, NatEntry> for active internet flows
    fun getActiveInternetFlows(): Map<String, NatEntry> {
        return gatewayNatService.getActiveFlows()
    }

    /* ---------------- CALLBACK ---------------- */

    interface PacketDeliveryListener {
        fun onPacketDelivered(payload: ByteArray)
    }
}
