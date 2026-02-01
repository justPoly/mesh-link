package com.orliczspace.mesh_link.network.crypto

import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import javax.crypto.KeyAgreement
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import java.util.concurrent.ConcurrentHashMap

class KeyExchangeManager(
    private val localNodeId: String,
    private val sendPacket: (MeshKeyExchangePacket) -> Unit
) {

    private val keyPairs = ConcurrentHashMap<String, KeyPair>()
    private val sessions = ConcurrentHashMap<String, NeighborSession>()

    /** Start DH with a neighbor */
    fun initiateKeyExchange(neighborNodeId: String) {
        val keyPair = generateKeyPair()
        keyPairs[neighborNodeId] = keyPair

        val publicKeyBytes = keyPair.public.encoded

        sendPacket(
            MeshKeyExchangePacket(
                sourceNodeId = localNodeId,
                destinationNodeId = neighborNodeId,
                publicKey = publicKeyBytes,
                isResponse = false
            )
        )
    }

    /** Handle incoming key exchange packet */
    fun onKeyExchangePacket(packet: MeshKeyExchangePacket) {
        val neighborId = packet.sourceNodeId

        val localKeyPair = keyPairs.getOrPut(neighborId) {
            generateKeyPair()
        }

        val sharedSecret = deriveSharedSecret(
            localKeyPair.private,
            packet.publicKey
        )

        sessions[neighborId] = NeighborSession(
            neighborNodeId = neighborId,
            sharedSecret = sharedSecret
        )

        // If this was the first message, respond with our public key
        if (!packet.isResponse) {
            sendPacket(
                MeshKeyExchangePacket(
                    sourceNodeId = localNodeId,
                    destinationNodeId = neighborId,
                    publicKey = localKeyPair.public.encoded,
                    isResponse = true
                )
            )
        }
    }

    fun getSession(neighborNodeId: String): NeighborSession? {
        return sessions[neighborNodeId]
    }

    // ================== CRYPTO ==================

    private fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("X25519")
        return generator.generateKeyPair()
    }

    private fun deriveSharedSecret(
        privateKey: java.security.PrivateKey,
        peerPublicKeyBytes: ByteArray
    ): SecretKey {
        val keyFactory = KeyFactory.getInstance("X25519")
        val peerPublicKey: PublicKey =
            keyFactory.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))

        val agreement = KeyAgreement.getInstance("X25519")
        agreement.init(privateKey)
        agreement.doPhase(peerPublicKey, true)

        val sharedSecretBytes = agreement.generateSecret()

        // Derive AES key (256-bit)
        return SecretKeySpec(sharedSecretBytes.copyOf(32), "AES")
    }
}
