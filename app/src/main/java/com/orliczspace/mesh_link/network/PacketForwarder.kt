package com.orliczspace.mesh_link.network

import android.util.Log
import com.orliczspace.mesh_link.network.gateway.FlowLogger
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
    flowLogger: FlowLogger,
    private val deliveryListener: PacketDeliveryListener
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null

    private val gatewayNatService = GatewayNatService(
        flowLogger = flowLogger,
        onInboundPacket = { payload ->
            deliveryListener.onPacketDelivered(payload)
        }
    )

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

    private fun listen() {
        scope.launch {
            val buffer = ByteArray(65535)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    handlePacket(packet)
                } catch (e: Exception) {
                    if (isActive) Log.e("PacketForwarder", "Receive error", e)
                }
            }
        }
    }

    private fun handlePacket(packet: DatagramPacket) {
        val forwardPacket = ForwardPacketCodec.decode(packet.data, packet.length)

        if (forwardPacket.ttl <= 0) return

        // Packet meant for this node
        if (forwardPacket.destinationNodeId == localNodeId) {
            deliver(forwardPacket)
            return
        }

        val route = routingRepository.getRouteToInternet(localNodeId)

        // Internet-bound and this node is gateway
        if (forwardPacket.destinationNodeId == null && route != null && !route.viaGateway) {
            gatewayNatService.handleOutbound(
                rawIpPacket = forwardPacket.payload,
                sourceNodeId = forwardPacket.sourceNodeId
            )
            return
        }

        // Forward inside mesh
        forward(forwardPacket)
    }

    fun send(packet: ForwardPacket) = forward(packet)

    fun forwardRawIpPacket(rawPacket: ByteArray) {
        val packet = ForwardPacket(
            sourceNodeId = localNodeId,
            destinationNodeId = null,
            ttl = 8,
            payload = rawPacket
        )
        forward(packet)
    }

    private fun forward(packet: ForwardPacket) {
        val route = routingRepository.getRouteToInternet(localNodeId) ?: return

        // Deliver locally if this node is gateway
        if (!route.viaGateway) {
            deliver(packet)
            return
        }

        val nextHopNodeId = route.nextHopNodeId ?: return
        val nextHopIp = linkProbeService.getKnownPeers()[nextHopNodeId] ?: return

        val forwardedPacket = packet.copy(ttl = packet.ttl - 1)
        val data = ForwardPacketCodec.encode(forwardedPacket)

        scope.launch {
            try {
                socket?.send(DatagramPacket(data, data.size, InetAddress.getByName(nextHopIp), listenPort))
                Log.d("PacketForwarder", "Forwarded packet to $nextHopNodeId @ $nextHopIp")
            } catch (e: Exception) {
                Log.e("PacketForwarder", "Forward error", e)
            }
        }
    }

    private fun deliver(packet: ForwardPacket) {
        Log.d("PacketForwarder", "Delivered packet from ${packet.sourceNodeId}")
        deliveryListener.onPacketDelivered(packet.payload)
    }

    fun getActiveInternetFlows(): Map<String, NatEntry> = gatewayNatService.getActiveFlows()

    interface PacketDeliveryListener {
        fun onPacketDelivered(payload: ByteArray)
    }
}
