package com.orliczspace.mesh_link.network

import android.util.Log
import com.orliczspace.mesh_link.network.gateway.GatewayNatService
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

    private val gatewayNatService = GatewayNatService { _, rawIpPacket ->
        // Return internet response back to VPN
        deliveryListener.onPacketDelivered(rawIpPacket)
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
        gatewayNatService.stop()
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
                    if (isActive) {
                        Log.e("PacketForwarder", "Receive error", e)
                    }
                }
            }
        }
    }

    private fun handlePacket(packet: DatagramPacket) {
        val forwardPacket = ForwardPacketCodec.decode(
            packet.data,
            packet.length
        )

        if (forwardPacket.ttl <= 0) {
            Log.d("PacketForwarder", "Dropped packet (TTL expired)")
            return
        }

        val route = routingRepository.getRouteToInternet(localNodeId)

        // Mesh packet destined for this node
        if (forwardPacket.destinationNodeId == localNodeId) {
            onPacketDelivered(forwardPacket)
            return
        }

        // Internet-bound packet and we are gateway
        if (forwardPacket.destinationNodeId == null &&
            route != null &&
            !route.viaGateway
        ) {
            gatewayNatService.handleOutbound(forwardPacket.payload)
            return
        }

        forward(forwardPacket)
    }

    /* ---------------- FORWARD ---------------- */

    fun send(packet: ForwardPacket) {
        forward(packet)
    }

    private fun forward(packet: ForwardPacket) {
        val route = routingRepository.getRouteToInternet(localNodeId)

        if (route == null) {
            Log.d("PacketForwarder", "No route available, dropping packet")
            return
        }

        if (!route.viaGateway) {
            onPacketDelivered(packet)
            return
        }

        val nextHopNodeId = route.nextHopNodeId ?: return
        val nextHopIp = linkProbeService.getKnownPeers()[nextHopNodeId]

        if (nextHopIp == null) {
            Log.d("PacketForwarder", "Unknown next hop IP for $nextHopNodeId")
            return
        }

        val forwardedPacket = packet.copy(ttl = packet.ttl - 1)
        val data = ForwardPacketCodec.encode(forwardedPacket)

        scope.launch {
            try {
                socket?.send(
                    DatagramPacket(
                        data,
                        data.size,
                        InetAddress.getByName(nextHopIp),
                        listenPort
                    )
                )
            } catch (e: Exception) {
                Log.e("PacketForwarder", "Forward error", e)
            }
        }
    }

    /* ---------------- DELIVERY ---------------- */

    private fun onPacketDelivered(packet: ForwardPacket) {
        deliveryListener.onPacketDelivered(packet.payload)
    }

    interface PacketDeliveryListener {
        fun onPacketDelivered(payload: ByteArray)
    }
}
