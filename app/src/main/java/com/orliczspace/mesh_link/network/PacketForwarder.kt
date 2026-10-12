
package com.orliczspace.mesh_link.network

import android.util.Log
import com.orliczspace.mesh_link.network.gateway.GatewayNatService
import com.orliczspace.mesh_link.network.gateway.NatEntry
import com.orliczspace.mesh_link.network.packet.MeshPacket
import com.orliczspace.mesh_link.network.packet.PacketType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
        private const val MESH_PORT = 9999
        private const val MAX_PACKET_SIZE = 65_507
        private const val DEDUP_WINDOW_MS = 10_000L
    }

    lateinit var meshNetworkManager: MeshNetworkManager

    @Volatile
    var localNodeId: String = ""

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var receiverJob: Job? = null

    private data class PeerAddress(
        val address: InetAddress,
        val port: Int
    )

    private val peerAddresses = ConcurrentHashMap<String, PeerAddress>()
    private val seenPackets = ConcurrentHashMap<String, Long>()

    interface PacketDeliveryListener {
        fun onPacketDelivered(payload: ByteArray)
    }

    var deliveryListener: PacketDeliveryListener? = null

    /**
     * Register a peer using its real network address.
     * Do not register localhost or guessed routing-table addresses.
     */
    fun registerPeerAddress(
        nodeId: String,
        address: InetAddress,
        port: Int = MESH_PORT
    ) {
        if (nodeId.isBlank() || nodeId == localNodeId) return
        if (port !in 1..65535) return

        peerAddresses[nodeId] = PeerAddress(address, port)

        Log.d(TAG, "Peer address registered: $nodeId -> ${address.hostAddress}:$port")
    }

    fun removePeerAddress(nodeId: String) {
        peerAddresses.remove(nodeId)
    }

    /**
     * Start receiving serialized MeshPacket datagrams.
     * The supplied socket must be bound to the mesh transport port.
     */
    @Synchronized
    fun startReceiver() {
        check(!socket.isClosed) {
            "Mesh UDP socket is closed"
        }

        check(socket.localPort == MESH_PORT) {
            "Mesh UDP socket must be bound to port $MESH_PORT; actual port=${socket.localPort}"
        }

        if (receiverJob?.isActive == true) return

        receiverJob = scope.launch {
            val buffer = ByteArray(MAX_PACKET_SIZE)

            Log.d(TAG, "Mesh UDP receiver started on port ${socket.localPort}")

            while (isActive && !socket.isClosed) {
                try {
                    val datagram = DatagramPacket(buffer, buffer.size)
                    socket.receive(datagram)

                    onDatagramReceived(
                        data = datagram.data,
                        length = datagram.length,
                        sender = datagram.address,
                        port = datagram.port
                    )
                } catch (e: Exception) {
                    if (isActive && !socket.isClosed) {
                        Log.e(TAG, "Mesh UDP receive error", e)
                    }
                }
            }

            Log.d(TAG, "Mesh UDP receiver stopped")
        }
    }

    /**
     * Send a serialized mesh packet directly.
     * Never call MeshNetworkManager.sendSecurePacket() here:
     * that would recursively call this forwarding method.
     */
    suspend fun forward(packet: MeshPacket) {
        if (packet.ttl <= 0) {
            Log.w(TAG, "Dropping packet ${packet.packetId}: TTL expired")
            return
        }

        if (packet.type == PacketType.NAT_FORWARD) {
            gatewayNatService.handleOutbound(
                packet.payload,
                packet.sourceNodeId
            )
            return
        }

        val destinationId = packet.destinationNodeId

        if (destinationId.isNullOrBlank()) {
            Log.w(TAG, "Cannot forward packet without a destination")
            return
        }

        val peer = peerAddresses[destinationId]

        if (peer == null) {
            Log.w(TAG, "No registered address for destination $destinationId")
            return
        }

        val bytes = packet.serialize()

        require(bytes.size <= MAX_PACKET_SIZE) {
            "Serialized mesh packet exceeds UDP payload limit"
        }

        val datagram = DatagramPacket(
            bytes,
            bytes.size,
            InetSocketAddress(peer.address, peer.port)
        )

        socket.send(datagram)

        Log.d(
            TAG,
            "Sent ${packet.type} packet ${packet.packetId} " +
                    "to $destinationId at ${peer.address.hostAddress}:${peer.port}"
        )
    }

    /**
     * Process an incoming serialized MeshPacket.
     */
    fun onDatagramReceived(
        data: ByteArray,
        length: Int,
        sender: InetAddress,
        port: Int
    ) {
        val packet = try {
            MeshPacket.deserialize(data, length)
        } catch (e: Exception) {
            Log.w(TAG, "Rejected malformed mesh datagram from ${sender.hostAddress}", e)
            return
        }

        if (packet.sourceNodeId.isBlank() || packet.sourceNodeId == localNodeId) {
            Log.w(TAG, "Ignoring invalid or self-originated mesh packet")
            return
        }

        if (isDuplicate(packet.packetId)) {
            Log.d(TAG, "Ignoring duplicate packet ${packet.packetId}")
            return
        }

        // Learn the sender's real address from the received datagram.
        registerPeerAddress(
            nodeId = packet.sourceNodeId,
            address = sender,
            port = port
        )

        Log.d(
            TAG,
            "Received ${packet.type} packet ${packet.packetId} " +
                    "from ${packet.sourceNodeId} at ${sender.hostAddress}:$port"
        )

        if (packet.destinationNodeId == localNodeId) {
            if (!::meshNetworkManager.isInitialized) {
                Log.e(TAG, "MeshNetworkManager is not initialized")
                return
            }

            meshNetworkManager.onPacketReceived(packet, sender)
            return
        }

        if (packet.destinationNodeId == null) {
            Log.d(TAG, "Received packet without a destination; not forwarding")
            return
        }

        if (packet.ttl <= 1) {
            Log.w(TAG, "Dropping packet ${packet.packetId}: TTL exhausted")
            return
        }

        scope.launch {
            try {
                forward(packet.copy(ttl = packet.ttl - 1))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to forward packet ${packet.packetId}", e)
            }
        }
    }

    private fun isDuplicate(packetId: String): Boolean {
        val now = System.currentTimeMillis()

        seenPackets.entries.removeIf {
            now - it.value > DEDUP_WINDOW_MS
        }

        return seenPackets.putIfAbsent(packetId, now) != null
    }

    fun getActiveInternetFlows(): Map<String, NatEntry> {
        return gatewayNatService.getActiveFlows()
    }

    @Synchronized
    fun stop() {
        receiverJob?.cancel()
        receiverJob = null
        scope.cancel()
        socket.close()
        peerAddresses.clear()
        seenPackets.clear()
        Log.d(TAG, "Mesh transport stopped")
    }
}
