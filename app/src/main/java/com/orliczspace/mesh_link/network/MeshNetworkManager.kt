package com.orliczspace.mesh_link.network

import android.util.Log
import com.orliczspace.mesh_link.network.packet.MeshPacket
import com.orliczspace.mesh_link.network.packet.PacketType
import com.orliczspace.mesh_link.network.security.CryptoManager
import kotlinx.coroutines.*
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
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

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Neighbor sessions: neighborId -> Session
    private val sessions = ConcurrentHashMap<String, Session>()

    // Replay protection: neighborId -> last counter seen
    private val replayCounters = ConcurrentHashMap<String, Long>()

    data class Session(
        val keyPair: KeyPair,
        var sharedSecret: ByteArray? = null
    )

    /** ------------------ Key Exchange ------------------ */

    fun initiateKeyExchange(neighborId: String) {
        val keyPair = CryptoManager.generateDHKeyPair()
        sessions[neighborId] = Session(keyPair)

        // Send public key to neighbor
        val packet = MeshPacket(
            sourceNodeId = localNodeId,
            destinationNodeId = neighborId,
            payload = keyPair.public.encoded,
            type = PacketType.KEY_EXCHANGE
        )

        sendPacket(packet)
        Log.d(TAG, "Initiated DH key exchange with $neighborId")
    }

    fun processKeyExchangeMessage(fromNode: String, publicKeyBytes: ByteArray) {
        val session = sessions.getOrPut(fromNode) {
            Session(CryptoManager.generateDHKeyPair())
        }

        // Compute shared secret
        session.sharedSecret = CryptoManager.computeSharedSecret(
            session.keyPair.private,
            publicKeyBytes
        )

        // If we haven't sent our public key yet, send it
        if (session.keyPair.public.encoded.isNotEmpty()) {
            val reply = MeshPacket(
                sourceNodeId = localNodeId,
                destinationNodeId = fromNode,
                payload = session.keyPair.public.encoded,
                type = PacketType.KEY_EXCHANGE
            )
            sendPacket(reply)
        }

        Log.d(TAG, "Established secure session with $fromNode")
    }

    /** ------------------ Sending Packets ------------------ */

    fun sendSecurePacket(
        neighborId: String,
        plainPayload: ByteArray,
        packetType: PacketType = PacketType.DATA
    ) {
        val session = sessions[neighborId]?.sharedSecret
            ?: run {
                Log.w(TAG, "No secure session with $neighborId")
                initiateKeyExchange(neighborId)
                return
            }

        // Increment replay counter
        val counter = (replayCounters[neighborId] ?: 0L) + 1
        replayCounters[neighborId] = counter

        val encrypted = CryptoManager.encrypt(plainPayload, session, counter)

        val packet = MeshPacket(
            sourceNodeId = localNodeId,
            destinationNodeId = neighborId,
            payload = encrypted,
            type = packetType,
            encrypted = true
        )

        sendPacket(packet)
    }

    private fun sendPacket(packet: MeshPacket) {
        scope.launch {
            try {
                packetForwarder.forward(packet)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send packet to ${packet.destinationNodeId}", e)
            }
        }
    }

    /** ------------------ Receiving Packets ------------------ */

    fun onPacketReceived(packet: MeshPacket, sender: InetAddress) {
        scope.launch {
            try {
                when (packet.type) {
                    PacketType.KEY_EXCHANGE -> {
                        processKeyExchangeMessage(packet.sourceNodeId, packet.payload)
                    }

                    PacketType.DATA -> {
                        if (packet.encrypted) {
                            val session = sessions[packet.sourceNodeId]?.sharedSecret
                            if (session == null) {
                                Log.w(TAG, "No session with ${packet.sourceNodeId}, dropping packet")
                                return@launch
                            }

                            // Check replay counter
                            val counter = extractCounter(packet.payload)
                            val lastCounter = replayCounters[packet.sourceNodeId] ?: 0L
                            if (counter <= lastCounter) {
                                Log.w(TAG, "Replay packet detected from ${packet.sourceNodeId}")
                                return@launch
                            }
                            replayCounters[packet.sourceNodeId] = counter

                            val decrypted = CryptoManager.decrypt(packet.payload, session)
                            val securePacket = packet.copy(payload = decrypted, encrypted = false)
                            packetForwarder.deliveryListener?.onPacketDelivered(securePacket.payload)
                        } else {
                            packetForwarder.deliveryListener?.onPacketDelivered(packet.payload)
                        }
                    }

                    else -> {
                        packetForwarder.onDatagramReceived(packet.payload, packet.payload.size, sender, 0)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing packet from ${packet.sourceNodeId}", e)
            }
        }
    }

    private fun extractCounter(payload: ByteArray): Long {
        // AES-GCM prepends 12-byte IV + 8-byte counter
        return ByteBuffer.wrap(payload, 12, 8).long
    }
}
