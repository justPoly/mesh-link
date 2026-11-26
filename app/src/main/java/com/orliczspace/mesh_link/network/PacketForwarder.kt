package com.orliczspace.mesh_link.network

import android.os.SystemClock
import android.util.Log
import com.orliczspace.mesh_link.network.gateway.GatewayNatService
import com.orliczspace.mesh_link.network.gateway.NatEntry
import com.orliczspace.mesh_link.network.packet.MeshPacket
import com.orliczspace.mesh_link.network.packet.PacketType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    @Volatile
    private var stopped = false

    private val scope =
        CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var receiverJob: Job? = null

    // ---------------------------------------------------------
    // Peer endpoint registry
    // ---------------------------------------------------------

    private data class PeerAddress(
        val address: InetAddress,
        val port: Int
    )

    private val peerAddresses =
        ConcurrentHashMap<String, PeerAddress>()

    // ---------------------------------------------------------
    // Duplicate packet tracking
    // ---------------------------------------------------------

    private data class PacketIdentity(
        val sourceNodeId: String,
        val packetId: String
    )

    private val seenPackets =
        ConcurrentHashMap<PacketIdentity, Long>()

    // ---------------------------------------------------------
    // Packet delivery listener
    // ---------------------------------------------------------

    interface PacketDeliveryListener {
        fun onPacketDelivered(payload: ByteArray)
    }

    @Volatile
    var deliveryListener: PacketDeliveryListener? = null

    // ---------------------------------------------------------
    // Register verified peer endpoint
    // ---------------------------------------------------------

    fun registerPeerAddress(
        nodeId: String,
        address: InetAddress,
        port: Int = MESH_PORT
    ) {

        if (stopped) return

        if (
            nodeId.isBlank() ||
            nodeId == localNodeId
        ) {
            return
        }

        if (port !in 1..65535) return

        if (
            address.isLoopbackAddress ||
            address.isAnyLocalAddress ||
            address.isMulticastAddress
        ) {

            Log.w(
                TAG,
                "Rejected invalid peer address: " +
                        address.hostAddress
            )

            return
        }

        val endpoint =
            PeerAddress(address, port)

        val previous =
            peerAddresses.put(nodeId, endpoint)

        if (previous != endpoint) {

            Log.d(
                TAG,
                "ENDPOINT_REGISTERED: $nodeId -> " +
                        "${address.hostAddress}:$port"
            )
        }
    }

    // ---------------------------------------------------------
    // Remove stale peer endpoint
    // ---------------------------------------------------------

    fun removePeerAddress(nodeId: String) {

        val removed =
            peerAddresses.remove(nodeId)

        if (removed != null) {

            Log.w(
                TAG,
                "ENDPOINT_REMOVED: $nodeId -> " +
                        "${removed.address.hostAddress}:" +
                        "${removed.port}"
            )
        }
    }

    // ---------------------------------------------------------
    // Endpoint diagnostics
    // ---------------------------------------------------------

    fun hasPeerAddress(nodeId: String): Boolean {

        return peerAddresses.containsKey(nodeId)
    }

    fun getRegisteredPeerCount(): Int {

        return peerAddresses.size
    }

    fun getRegisteredPeerAddresses():
            Map<String, InetSocketAddress> {

        return peerAddresses.mapValues { entry ->

            InetSocketAddress(
                entry.value.address,
                entry.value.port
            )
        }
    }

    // ---------------------------------------------------------
    // Start mesh UDP receiver
    // ---------------------------------------------------------

    @Synchronized
    fun startReceiver() {

        check(!stopped) {
            "PacketForwarder has already been stopped"
        }

        check(!socket.isClosed) {
            "Mesh UDP socket is closed"
        }

        check(socket.localPort == MESH_PORT) {
            "Mesh UDP socket must be bound to port " +
                    "$MESH_PORT; actual port=${socket.localPort}"
        }

        if (receiverJob?.isActive == true) return

        receiverJob = scope.launch {

            val buffer =
                ByteArray(MAX_PACKET_SIZE)

            Log.d(
                TAG,
                "Mesh UDP receiver started on port " +
                        socket.localPort
            )

            while (
                isActive &&
                !stopped &&
                !socket.isClosed
            ) {

                try {

                    val datagram =
                        DatagramPacket(
                            buffer,
                            buffer.size
                        )

                    socket.receive(datagram)

                    if (stopped) break

                    onDatagramReceived(
                        data = datagram.data,
                        length = datagram.length,
                        sender = datagram.address,
                        port = datagram.port
                    )

                } catch (e: CancellationException) {

                    throw e

                } catch (e: Exception) {

                    if (
                        isActive &&
                        !stopped &&
                        !socket.isClosed
                    ) {

                        Log.e(
                            TAG,
                            "Mesh UDP receive error",
                            e
                        )
                    }
                }
            }

            Log.d(
                TAG,
                "Mesh UDP receiver stopped"
            )
        }
    }

    // ---------------------------------------------------------
    // Send outgoing mesh packet
    // ---------------------------------------------------------

    suspend fun forward(packet: MeshPacket) {

        withContext(Dispatchers.IO) {

            if (stopped || socket.isClosed) {
                return@withContext
            }

            if (packet.ttl <= 0) {

                Log.w(
                    TAG,
                    "Dropping packet ${packet.packetId}: " +
                            "TTL expired"
                )

                return@withContext
            }

            val destinationId =
                packet.destinationNodeId

            if (destinationId.isNullOrBlank()) {

                Log.w(
                    TAG,
                    "Cannot forward packet without destination"
                )

                return@withContext
            }

            // Remote internet forwarding is disabled during
            // local mesh diagnostics.

            if (packet.type == PacketType.NAT_FORWARD) {

                Log.w(
                    TAG,
                    "NAT_FORWARD disabled during local testing"
                )

                return@withContext
            }

            if (destinationId == localNodeId) {

                Log.w(
                    TAG,
                    "Outgoing packet addressed to local node"
                )

                return@withContext
            }

            val peer =
                peerAddresses[destinationId]

            if (peer == null) {

                Log.w(
                    TAG,
                    "NO_ENDPOINT: destination=$destinationId | " +
                            "packet=${packet.packetId}"
                )

                return@withContext
            }

            try {

                val bytes =
                    packet.serialize()

                if (bytes.size > MAX_PACKET_SIZE) {

                    Log.w(
                        TAG,
                        "Serialized packet exceeds UDP limit"
                    )

                    return@withContext
                }

                val datagram =
                    DatagramPacket(
                        bytes,
                        bytes.size,
                        InetSocketAddress(
                            peer.address,
                            peer.port
                        )
                    )

                socket.send(datagram)

                Log.d(
                    TAG,
                    "PACKET_SENT: " +
                            "type=${packet.type} | " +
                            "id=${packet.packetId} | " +
                            "destination=$destinationId | " +
                            "endpoint=${peer.address.hostAddress}:" +
                            "${peer.port} | " +
                            "ttl=${packet.ttl} | " +
                            "bytes=${bytes.size}"
                )

            } catch (e: CancellationException) {

                throw e

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed to send packet ${packet.packetId}",
                    e
                )
            }
        }
    }

    // ---------------------------------------------------------
    // Process incoming mesh datagram
    // ---------------------------------------------------------

    fun onDatagramReceived(
        data: ByteArray,
        length: Int,
        sender: InetAddress,
        port: Int
    ) {

        if (stopped) return

        if (
            length <= 0 ||
            length > data.size ||
            length > MAX_PACKET_SIZE
        ) {

            Log.w(
                TAG,
                "Rejected invalid datagram length: $length"
            )

            return
        }

        val packet = try {

            MeshPacket.deserialize(
                data,
                length
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Rejected malformed mesh datagram from " +
                        "${sender.hostAddress}:$port",
                e
            )

            return
        }

        // Validate source identity.

        if (
            packet.sourceNodeId.isBlank() ||
            packet.sourceNodeId == localNodeId
        ) {

            Log.w(
                TAG,
                "Ignoring invalid or self-originated packet"
            )

            return
        }

        // Validate packet lifetime.

        if (packet.ttl <= 0) {

            Log.w(
                TAG,
                "Rejected expired packet ${packet.packetId}"
            )

            return
        }

        val destinationId =
            packet.destinationNodeId

        if (destinationId.isNullOrBlank()) {

            Log.w(
                TAG,
                "Rejected packet without destination"
            )

            return
        }

        // Prevent another device from consuming this
        // smartphone's internet data during testing.

        if (packet.type == PacketType.NAT_FORWARD) {

            Log.w(
                TAG,
                "Rejected NAT_FORWARD during local testing"
            )

            return
        }

        // -----------------------------------------------------
        // Validate immediate sender endpoint
        // -----------------------------------------------------

        val isKnownEndpoint =
            peerAddresses.values.any { endpoint ->

                endpoint.address == sender &&
                        endpoint.port == port
            }

        if (!isKnownEndpoint) {

            Log.w(
                TAG,
                "UNKNOWN_ENDPOINT: " +
                        "${sender.hostAddress}:$port"
            )

            return
        }

        // -----------------------------------------------------
        // Direct mesh diagnostic validation
        // -----------------------------------------------------

        if (
            packet.type == PacketType.RTT_PROBE ||
            packet.type == PacketType.RTT_RESPONSE
        ) {

            val expectedEndpoint =
                peerAddresses[packet.sourceNodeId]

            if (
                expectedEndpoint == null ||
                expectedEndpoint.address != sender ||
                expectedEndpoint.port != port ||
                packet.destinationNodeId != localNodeId ||
                packet.ttl != 1 ||
                packet.encrypted ||
                packet.payload.isNotEmpty()
            ) {

                Log.w(
                    TAG,
                    "Rejected invalid direct mesh diagnostic packet"
                )

                return
            }
        }

        // -----------------------------------------------------
        // Duplicate detection
        // -----------------------------------------------------

        if (
            isDuplicate(
                sourceNodeId = packet.sourceNodeId,
                packetId = packet.packetId
            )
        ) {

            Log.d(
                TAG,
                "Ignoring duplicate packet ${packet.packetId}"
            )

            return
        }

        Log.d(
            TAG,
            "PACKET_RECEIVED: " +
                    "type=${packet.type} | " +
                    "id=${packet.packetId} | " +
                    "source=${packet.sourceNodeId} | " +
                    "destination=$destinationId | " +
                    "sender=${sender.hostAddress}:$port | " +
                    "ttl=${packet.ttl}"
        )

        // -----------------------------------------------------
        // Deliver packets intended for this device
        // -----------------------------------------------------

        if (destinationId == localNodeId) {

            deliverLocalPacket(
                packet = packet,
                sender = sender
            )

            return
        }

        // -----------------------------------------------------
        // Relay packets intended for another device
        // -----------------------------------------------------

        if (packet.ttl <= 1) {

            Log.w(
                TAG,
                "Dropping packet ${packet.packetId}: " +
                        "TTL exhausted"
            )

            return
        }

        scope.launch {

            try {

                val forwardedPacket =
                    packet.copy(
                        ttl = packet.ttl - 1
                    )

                Log.d(
                    TAG,
                    "PACKET_RELAY: " +
                            "id=${packet.packetId} | " +
                            "destination=$destinationId | " +
                            "ttl=${forwardedPacket.ttl}"
                )

                forward(forwardedPacket)

            } catch (e: CancellationException) {

                throw e

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed to relay packet ${packet.packetId}",
                    e
                )
            }
        }
    }

    // ---------------------------------------------------------
    // Deliver local mesh packet
    // ---------------------------------------------------------

    private fun deliverLocalPacket(
        packet: MeshPacket,
        sender: InetAddress
    ) {

        if (stopped) return

        if (!::meshNetworkManager.isInitialized) {

            Log.e(
                TAG,
                "MeshNetworkManager is not initialized"
            )

            return
        }

        try {

            meshNetworkManager.onPacketReceived(
                packet,
                sender
            )

            Log.d(
                TAG,
                "PACKET_DISPATCHED: " +
                        "id=${packet.packetId} | " +
                        "type=${packet.type}"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Local packet dispatch failed",
                e
            )
        }
    }

    // ---------------------------------------------------------
    // Duplicate packet detection
    // ---------------------------------------------------------

    private fun isDuplicate(
        sourceNodeId: String,
        packetId: String
    ): Boolean {

        val now =
            SystemClock.elapsedRealtime()

        seenPackets.entries.removeIf { entry ->

            now - entry.value > DEDUP_WINDOW_MS
        }

        val identity =
            PacketIdentity(
                sourceNodeId = sourceNodeId,
                packetId = packetId
            )

        return seenPackets.putIfAbsent(
            identity,
            now
        ) != null
    }

    // ---------------------------------------------------------
    // Active internet flows
    // ---------------------------------------------------------

    fun getActiveInternetFlows(): Map<String, NatEntry> {

        return gatewayNatService.getActiveFlows()
    }

    // ---------------------------------------------------------
    // Stop mesh transport
    // ---------------------------------------------------------

    @Synchronized
    fun stop() {

        if (stopped) return

        stopped = true

        receiverJob?.cancel()

        receiverJob = null

        socket.close()

        scope.cancel()

        peerAddresses.clear()

        seenPackets.clear()

        deliveryListener = null

        Log.d(
            TAG,
            "Mesh transport stopped"
        )
    }
}
