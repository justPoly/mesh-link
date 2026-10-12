
package com.orliczspace.mesh_link.network

import android.util.Log
import com.orliczspace.mesh_link.network.packet.MeshPacket
import com.orliczspace.mesh_link.network.packet.PacketType
import com.orliczspace.mesh_link.network.security.CryptoManager
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
        CoroutineScope(Dispatchers.IO + SupervisorJob())

    data class Session(
        val keyPair: KeyPair,
        @Volatile var sharedSecret: ByteArray? = null
    )

    private val sessions = ConcurrentHashMap<String, Session>()

    // Keep transmit and receive counters independent.
    private val outgoingCounters = ConcurrentHashMap<String, Long>()
    private val incomingCounters = ConcurrentHashMap<String, Long>()

    fun initiateKeyExchange(neighborId: String) {
        if (neighborId.isBlank() || neighborId == localNodeId) return

        val existing = sessions[neighborId]
        if (existing?.sharedSecret != null) {
            Log.d(TAG, "Secure session already exists with $neighborId")
            return
        }

        val session = existing ?: Session(CryptoManager.generateDHKeyPair())
            .also { sessions[neighborId] = it }

        sendPacket(
            MeshPacket(
                sourceNodeId = localNodeId,
                destinationNodeId = neighborId,
                payload = session.keyPair.public.encoded,
                type = PacketType.KEY_EXCHANGE
            )
        )

        Log.d(TAG, "Initiated key exchange with $neighborId")
    }

    fun processKeyExchangeMessage(
        fromNode: String,
        publicKeyBytes: ByteArray
    ) {
        if (fromNode.isBlank() || fromNode == localNodeId) return

        try {
            var shouldReply = false

            val session = synchronized(sessions) {
                sessions[fromNode] ?: Session(
                    CryptoManager.generateDHKeyPair()
                ).also {
                    sessions[fromNode] = it
                    shouldReply = true
                }
            }

            val secret = CryptoManager.computeSharedSecret(
                session.keyPair.private,
                publicKeyBytes
            )

            session.sharedSecret = secret

            // Reply only when creating a session for an incoming request.
            // An established session must not reply forever to each key packet.
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

            Log.d(TAG, "Secure session established with $fromNode")
        } catch (e: Exception) {
            Log.e(TAG, "Key exchange failed with $fromNode", e)
        }
    }

    fun sendSecurePacket(
        neighborId: String,
        plainPayload: ByteArray,
        packetType: PacketType = PacketType.DATA
    ) {
        val session = sessions[neighborId]

        if (session?.sharedSecret == null) {
            Log.w(TAG, "No secure session with $neighborId; starting exchange")

            initiateKeyExchange(neighborId)
            return
        }

        try {
            val counter = outgoingCounters.merge(
                neighborId,
                1L
            ) { old, increment -> old + increment } ?: 1L

            val encrypted = CryptoManager.encrypt(
                plainPayload,
                session.sharedSecret!!,
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
            Log.e(TAG, "Secure send failed to $neighborId", e)
        }
    }

    private fun sendPacket(packet: MeshPacket) {
        scope.launch {
            try {
                // PacketForwarder now sends serialized packets directly.
                packetForwarder.forward(packet)
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Failed to send packet to ${packet.destinationNodeId}",
                    e
                )
            }
        }
    }

    fun onPacketReceived(
        packet: MeshPacket,
        sender: InetAddress
    ) {
        scope.launch {
            try {
                if (packet.destinationNodeId != null &&
                    packet.destinationNodeId != localNodeId
                ) {
                    Log.w(TAG, "Packet addressed to another node; ignoring")
                    return@launch
                }

                when (packet.type) {
                    PacketType.KEY_EXCHANGE -> {
                        processKeyExchangeMessage(
                            packet.sourceNodeId,
                            packet.payload
                        )
                    }

                    PacketType.DATA -> {
                        var payload = packet.payload

                        if (packet.encrypted) {
                            val key = sessions[
                                packet.sourceNodeId
                            ]?.sharedSecret

                            if (key == null) {
                                Log.w(
                                    TAG,
                                    "No secure session with ${packet.sourceNodeId}"
                                )
                                return@launch
                            }

                            val decrypted =
                                CryptoManager.decryptWithCounter(
                                    packet.payload,
                                    key
                                )

                            val previous =
                                incomingCounters[packet.sourceNodeId] ?: 0L

                            if (decrypted.counter <= previous) {
                                Log.w(
                                    TAG,
                                    "Rejected replay from ${packet.sourceNodeId}"
                                )
                                return@launch
                            }

                            incomingCounters[packet.sourceNodeId] =
                                decrypted.counter

                            payload = decrypted.plaintext
                        }

                        packetForwarder.deliveryListener
                            ?.onPacketDelivered(payload)

                        Log.d(
                            TAG,
                            "Delivered DATA from ${packet.sourceNodeId} " +
                                    "(${payload.size} bytes)"
                        )
                    }

                    else -> {
                        Log.d(
                            TAG,
                            "Received ${packet.type} from ${packet.sourceNodeId}; " +
                                    "handler not implemented yet"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Error processing packet from ${packet.sourceNodeId}",
                    e
                )
            }
        }
    }

    fun stop() {
        scope.cancel()
        sessions.clear()
        outgoingCounters.clear()
        incomingCounters.clear()

        // The socket is shared with PacketForwarder.
        // Its owner should close it through PacketForwarder.stop().
    }
}
