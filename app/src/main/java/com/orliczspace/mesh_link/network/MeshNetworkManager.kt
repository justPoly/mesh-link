package com.orliczspace.mesh_link.network

import android.util.Log
import com.orliczspace.mesh_link.network.packet.MeshPacket
import com.orliczspace.mesh_link.network.packet.PacketType
import com.orliczspace.mesh_link.network.security.CryptoManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.KeyPair
import java.util.concurrent.ConcurrentHashMap

class MeshNetworkManager(
    private val localNodeId: String,
    private val socket: DatagramSocket,
    private val packetForwarder: PacketForwarder
) {

    companion object {
        private const val TAG = "MeshNetworkManager"
    }

    private val scope =
        CoroutineScope(
            Dispatchers.IO + SupervisorJob()
        )

    @Volatile
    private var stopped = false

    // ---------------------------------------------------------
    // Mesh reachability diagnostics
    // ---------------------------------------------------------

    @Volatile
    var meshReachabilityService:
            MeshReachabilityService? = null

    // ---------------------------------------------------------
    // Secure peer sessions
    // ---------------------------------------------------------

    data class Session(
        val keyPair: KeyPair,

        @Volatile
        var sharedSecret: ByteArray? = null
    )

    private val sessions =
        ConcurrentHashMap<String, Session>()

    private val outgoingCounters =
        ConcurrentHashMap<String, Long>()

    private val incomingCounters =
        ConcurrentHashMap<String, Long>()

    // ---------------------------------------------------------
    // Initiate key exchange
    // ---------------------------------------------------------

    fun initiateKeyExchange(neighborId: String) {

        if (stopped) return

        if (
            neighborId.isBlank() ||
            neighborId == localNodeId
        ) {
            return
        }

        if (!packetForwarder.hasPeerAddress(neighborId)) {

            Log.w(
                TAG,
                "Cannot initiate key exchange: " +
                        "no endpoint for $neighborId"
            )

            return
        }

        scope.launch {

            try {

                val session =
                    synchronized(sessions) {

                        if (stopped) {
                            return@launch
                        }

                        sessions[neighborId]
                            ?: Session(
                                CryptoManager.generateDHKeyPair()
                            ).also {

                                sessions[neighborId] = it
                            }
                    }

                if (session.sharedSecret != null) {

                    Log.d(
                        TAG,
                        "Secure session already exists " +
                                "with $neighborId"
                    )

                    return@launch
                }

                sendPacket(
                    MeshPacket(
                        sourceNodeId = localNodeId,
                        destinationNodeId = neighborId,
                        payload = session.keyPair.public.encoded,
                        type = PacketType.KEY_EXCHANGE
                    )
                )

                Log.d(
                    TAG,
                    "Initiated key exchange with $neighborId"
                )

            } catch (e: CancellationException) {

                throw e

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed to initiate key exchange",
                    e
                )
            }
        }
    }

    // ---------------------------------------------------------
    // Process incoming key exchange
    // ---------------------------------------------------------

    fun processKeyExchangeMessage(
        fromNode: String,
        publicKeyBytes: ByteArray
    ) {

        if (stopped) return

        if (
            fromNode.isBlank() ||
            fromNode == localNodeId
        ) {
            return
        }

        if (!packetForwarder.hasPeerAddress(fromNode)) {

            Log.w(
                TAG,
                "Rejected key exchange from unknown peer"
            )

            return
        }

        try {

            var shouldReply = false

            val session =
                synchronized(sessions) {

                    if (stopped) return

                    sessions[fromNode]
                        ?: Session(
                            CryptoManager.generateDHKeyPair()
                        ).also {

                            sessions[fromNode] = it

                            shouldReply = true
                        }
                }

            val secret =
                CryptoManager.computeSharedSecret(
                    session.keyPair.private,
                    publicKeyBytes
                )

            synchronized(sessions) {

                if (
                    stopped ||
                    sessions[fromNode] !== session
                ) {
                    return
                }

                session.sharedSecret = secret
            }

            // Reply only when creating a session for an
            // incoming key exchange request.

            if (shouldReply) {

                sendPacket(
                    MeshPacket(
                        sourceNodeId = localNodeId,
                        destinationNodeId = fromNode,
                        payload = session.keyPair.public.encoded,
                        type = PacketType.KEY_EXCHANGE
                    )
                )
            }

            Log.d(
                TAG,
                "Secure session established with $fromNode"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Key exchange failed with $fromNode",
                e
            )
        }
    }

    // ---------------------------------------------------------
    // Check secure session availability
    // ---------------------------------------------------------

    fun hasSecureSession(nodeId: String): Boolean {

        return sessions[nodeId]?.sharedSecret != null
    }

    // ---------------------------------------------------------
    // Send encrypted mesh packet
    // ---------------------------------------------------------

    fun sendSecurePacket(
        neighborId: String,
        plainPayload: ByteArray,
        packetType: PacketType = PacketType.DATA
    ) {

        if (stopped) return

        if (
            neighborId.isBlank() ||
            neighborId == localNodeId
        ) {
            return
        }

        if (!packetForwarder.hasPeerAddress(neighborId)) {

            Log.w(
                TAG,
                "Cannot send secure packet: no peer endpoint"
            )

            return
        }

        if (packetType == PacketType.NAT_FORWARD) {

            Log.w(
                TAG,
                "Internet forwarding disabled during local testing"
            )

            return
        }

        val session =
            sessions[neighborId]

        val sharedSecret =
            session?.sharedSecret

        if (sharedSecret == null) {

            Log.w(
                TAG,
                "No secure session with $neighborId; " +
                        "starting exchange"
            )

            initiateKeyExchange(neighborId)

            return
        }

        try {

            val counter =
                outgoingCounters.merge(
                    neighborId,
                    1L
                ) { old, increment ->

                    old + increment

                } ?: 1L

            val encrypted =
                CryptoManager.encrypt(
                    plainPayload,
                    sharedSecret,
                    counter
                )

            sendPacket(
                MeshPacket(
                    sourceNodeId = localNodeId,
                    destinationNodeId = neighborId,
                    payload = encrypted,
                    type = packetType,
                    seqNumber = counter,
                    encrypted = true
                )
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Secure send failed to $neighborId",
                e
            )
        }
    }

    // ---------------------------------------------------------
    // Send serialized packet through PacketForwarder
    // ---------------------------------------------------------

    private fun sendPacket(packet: MeshPacket) {

        if (stopped) return

        scope.launch {

            try {

                packetForwarder.forward(packet)

            } catch (e: CancellationException) {

                throw e

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed to send packet to " +
                            packet.destinationNodeId,
                    e
                )
            }
        }
    }

    // ---------------------------------------------------------
    // Receive mesh packet
    // ---------------------------------------------------------

    fun onPacketReceived(
        packet: MeshPacket,
        sender: InetAddress
    ) {

        if (stopped) return

        scope.launch {

            try {

                if (stopped) return@launch

                if (packet.destinationNodeId != localNodeId) {

                    Log.w(
                        TAG,
                        "Packet addressed to another node"
                    )

                    return@launch
                }

                if (
                    packet.sourceNodeId.isBlank() ||
                    packet.sourceNodeId == localNodeId
                ) {

                    return@launch
                }

                // ---------------------------------------------
                // Mesh reachability diagnostics
                // ---------------------------------------------

                if (
                    packet.type == PacketType.RTT_PROBE ||
                    packet.type == PacketType.RTT_RESPONSE
                ) {

                    val handled =
                        meshReachabilityService
                            ?.handlePacket(packet) == true

                    if (!handled) {

                        Log.w(
                            TAG,
                            "Mesh diagnostic service unavailable"
                        )
                    }

                    return@launch
                }

                // ---------------------------------------------
                // Other mesh packet types
                // ---------------------------------------------

                when (packet.type) {

                    PacketType.KEY_EXCHANGE -> {

                        if (packet.encrypted) {

                            Log.w(
                                TAG,
                                "Rejected encrypted key exchange"
                            )

                            return@launch
                        }

                        processKeyExchangeMessage(
                            packet.sourceNodeId,
                            packet.payload
                        )
                    }

                    PacketType.DATA -> {

                        processDataPacket(packet)
                    }

                    PacketType.NAT_FORWARD -> {

                        Log.w(
                            TAG,
                            "NAT_FORWARD disabled during testing"
                        )
                    }

                    else -> {

                        Log.d(
                            TAG,
                            "Received ${packet.type} from " +
                                    "${packet.sourceNodeId} at " +
                                    "${sender.hostAddress}; " +
                                    "handler not implemented yet"
                        )
                    }
                }

            } catch (e: CancellationException) {

                throw e

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Error processing packet from " +
                            packet.sourceNodeId,
                    e
                )
            }
        }
    }

    // ---------------------------------------------------------
    // Process incoming DATA packet
    // ---------------------------------------------------------

    private fun processDataPacket(packet: MeshPacket) {

        if (stopped) return

        var payload =
            packet.payload

        if (packet.encrypted) {

            val session =
                sessions[packet.sourceNodeId]

            val key =
                session?.sharedSecret

            if (key == null) {

                Log.w(
                    TAG,
                    "No secure session with " +
                            packet.sourceNodeId
                )

                return
            }

            val decrypted =
                CryptoManager.decryptWithCounter(
                    packet.payload,
                    key
                )

            if (
                decrypted.counter != packet.seqNumber
            ) {

                Log.w(
                    TAG,
                    "Rejected inconsistent packet counter"
                )

                return
            }

            val accepted =
                synchronized(sessions) {

                    if (
                        stopped ||
                        sessions[packet.sourceNodeId] !== session
                    ) {

                        false

                    } else {

                        val previous =
                            incomingCounters[
                                packet.sourceNodeId
                            ] ?: 0L

                        if (decrypted.counter <= previous) {

                            false

                        } else {

                            incomingCounters[
                                packet.sourceNodeId
                            ] = decrypted.counter

                            true
                        }
                    }
                }

            if (!accepted) {

                Log.w(
                    TAG,
                    "Rejected replay or expired session from " +
                            packet.sourceNodeId
                )

                return
            }

            payload =
                decrypted.plaintext
        }

        packetForwarder.deliveryListener
            ?.onPacketDelivered(payload)

        Log.d(
            TAG,
            "Delivered DATA from ${packet.sourceNodeId} " +
                    "(${payload.size} bytes)"
        )
    }

    // ---------------------------------------------------------
    // Remove disconnected peer session
    // ---------------------------------------------------------

    fun removePeerSession(nodeId: String) {

        synchronized(sessions) {

            sessions.remove(nodeId)

            outgoingCounters.remove(nodeId)

            incomingCounters.remove(nodeId)
        }

        Log.d(
            TAG,
            "SESSION_REMOVED: $nodeId"
        )
    }

    // ---------------------------------------------------------
    // Stop network manager
    // ---------------------------------------------------------

    fun stop() {

        if (stopped) return

        stopped = true

        meshReachabilityService = null

        scope.cancel()

        synchronized(sessions) {

            sessions.clear()

            outgoingCounters.clear()

            incomingCounters.clear()
        }

        // The shared UDP socket is owned by PacketForwarder.
        // PacketForwarder.stop() is responsible for closing it.

        Log.d(
            TAG,
            "MeshNetworkManager stopped"
        )
    }
}
