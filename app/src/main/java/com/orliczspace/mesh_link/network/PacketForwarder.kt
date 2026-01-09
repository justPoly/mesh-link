package com.orliczspace.mesh_link.network

import android.util.Log
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
            val buffer = ByteArray(2048)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    handlePacket(packet)
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e(
                            "PacketForwarder",
                            "Receive error: ${e.message}"
                        )
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
            Log.d(
                "PacketForwarder",
                "Dropped packet (TTL expired)"
            )
            return
        }

        // Packet reached destination node
        if (forwardPacket.destinationNodeId == localNodeId) {
            onPacketDelivered(forwardPacket)
            return
        }

        forward(forwardPacket)
    }

    /* ---------------- FORWARD ---------------- */

    fun forwardRawIpPacket(rawPacket: ByteArray) {
        val forwardPacket = ForwardPacket(
            sourceNodeId = localNodeId,
            destinationNodeId = null, // internet-bound
            ttl = 8,
            payload = rawPacket
        )

        forward(forwardPacket)
    }

    private fun forward(packet: ForwardPacket) {
        val route = routingRepository.getRouteToInternet(localNodeId)

        if (route == null) {
            Log.d(
                "PacketForwarder",
                "No route available, dropping packet"
            )
            return
        }

        // If we are the gateway, consume locally
        if (!route.viaGateway) {
            onPacketDelivered(packet)
            return
        }

        val nextHopNodeId = route.nextHopNodeId ?: return

        val nextHopIp =
            linkProbeService.getKnownPeers()[nextHopNodeId]

        if (nextHopIp == null) {
            Log.d(
                "PacketForwarder",
                "Next hop IP unknown for $nextHopNodeId"
            )
            return
        }

        val forwardedPacket = packet.copy(
            ttl = packet.ttl - 1
        )

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

                Log.d(
                    "PacketForwarder",
                    "Forwarded packet to $nextHopNodeId @ $nextHopIp"
                )

            } catch (e: Exception) {
                Log.e(
                    "PacketForwarder",
                    "Forward error: ${e.message}"
                )
            }
        }
    }

    /* ---------------- DELIVERY ---------------- */

    private fun onPacketDelivered(packet: ForwardPacket) {
        Log.d(
            "PacketForwarder",
            "Packet delivered from ${packet.sourceNodeId} " +
                    "(${packet.payload.size} bytes)"
        )

        deliveryListener.onPacketDelivered(packet.payload)
    }
    /* ---------------- CALLBACK ---------------- */

    interface PacketDeliveryListener {
        fun onPacketDelivered(payload: ByteArray)
    }
}
