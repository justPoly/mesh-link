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
    private val listenPort: Int = 9999
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null

    fun start() {
        socket = DatagramSocket(listenPort)
        listen()
        Log.d("PacketForwarder", "Started on port $listenPort")
    }

    fun stop() {
        scope.cancel()
        socket?.close()
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

        if (forwardPacket.ttl <= 0) {
            Log.d("PacketForwarder", "Dropped packet (TTL expired)")
            return
        }

        // Packet reached destination
        if (forwardPacket.destinationNodeId == localNodeId) {
            onPacketDelivered(forwardPacket)
            return
        }

        forward(forwardPacket)
    }

    /* ---------------- FORWARD ---------------- */

    private fun forward(packet: ForwardPacket) {
        val route = routingRepository.getRouteToInternet(localNodeId)
        if (route == null) {
            Log.d("PacketForwarder", "No route available, dropping packet")
            return
        }

        val nextHopIp =
            linkProbeService.getKnownPeers()[route.nextHopNodeId]

        if (nextHopIp == null) {
            Log.d(
                "PacketForwarder",
                "Next hop IP unknown for ${route.nextHopNodeId}"
            )
            return
        }

        val forwarded = packet.copy(ttl = packet.ttl - 1)
        val data = ForwardPacketCodec.encode(forwarded)

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
                    "Forwarded packet to ${route.nextHopNodeId} @ $nextHopIp"
                )

            } catch (e: Exception) {
                Log.e("PacketForwarder", "Forward error: ${e.message}")
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

        // Future: pass payload to app / proxy layer
    }
}
