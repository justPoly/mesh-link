package com.orliczspace.mesh_link.network

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import com.orliczspace.mesh_link.network.gateway.GatewayNatService

class PacketForwarder(
    private val localNodeId: String,
    private val routingRepository: RoutingStateRepository,
    private val linkProbeService: LinkProbeService,
    private val listenPort: Int = 9999,
    private val deliveryListener: PacketDeliveryListener
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null

    private val gatewayNatService = GatewayNatService { response ->
        // Internet → Mesh return path
        deliveryListener.onPacketDelivered(response)
    }

    /* ---------------- LIFECYCLE ---------------- */

    fun start() {
        socket = DatagramSocket(listenPort)
        gatewayNatService.start()
        listen()
        Log.d("PacketForwarder", "Started on port $listenPort")
    }

    fun stop() {
        scope.cancel()
        gatewayNatService.stop()
        socket?.close()
        socket = null
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
                        Log.e("PacketForwarder", "Receive error: ${e.message}")
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

        if (forwardPacket.ttl <= 0) return

        val route = routingRepository.getRouteToInternet(localNodeId)

        // Case 1: Packet addressed to this node
        if (forwardPacket.destinationNodeId == localNodeId) {
            onPacketDelivered(forwardPacket)
            return
        }

        // Case 2: Internet-bound and we are gateway
        if (forwardPacket.destinationNodeId == null &&
            route != null &&
            !route.viaGateway
        ) {
            gatewayNatService.sendToInternet(
                forwardPacket.payload,
                InetAddress.getByName("8.8.8.8") // Example destination
            )
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
            ?: return

        if (!route.viaGateway) {
            onPacketDelivered(packet)
            return
        }

        val nextHopNodeId = route.nextHopNodeId ?: return
        val nextHopIp = linkProbeService.getKnownPeers()[nextHopNodeId]
            ?: return

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
                Log.e("PacketForwarder", "Forward error: ${e.message}")
            }
        }
    }

    /* ---------------- DELIVERY ---------------- */

    private fun onPacketDelivered(packet: ForwardPacket) {
        deliveryListener.onPacketDelivered(packet.payload)
    }

    /* ---------------- CALLBACK ---------------- */

    interface PacketDeliveryListener {
        fun onPacketDelivered(payload: ByteArray)
    }
}
