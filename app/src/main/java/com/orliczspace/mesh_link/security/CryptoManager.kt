
package com.orliczspace.mesh_link.network.security

import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {

    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128
    private const val IV_LENGTH = 12
    private const val COUNTER_LENGTH = 8
    private const val AES_KEY_LENGTH = 16

    private val random = SecureRandom()

    data class DecryptedPayload(
        val counter: Long,
        val plaintext: ByteArray
    )

    // =========================================================
    // AES-GCM ENCRYPTION
    // =========================================================

    /**
     * Encrypts plaintext and embeds its counter inside the authenticated
     * ciphertext. The counter is verified only after successful decryption.
     *
     * Output format:
     * [12-byte IV][AES-GCM ciphertext and 16-byte authentication tag]
     */
    fun encrypt(
        plain: ByteArray,
        keyBytes: ByteArray,
        counter: Long = 0L
    ): ByteArray {
        require(keyBytes.size == AES_KEY_LENGTH) {
            "AES key must be exactly $AES_KEY_LENGTH bytes"
        }

        require(counter >= 0L) {
            "Encryption counter cannot be negative"
        }

        val iv = ByteArray(IV_LENGTH)
        random.nextBytes(iv)

        val counterBytes = ByteBuffer
            .allocate(COUNTER_LENGTH)
            .putLong(counter)
            .array()

        val payload = counterBytes + plain
        val key = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(AES_MODE)

        cipher.init(
            Cipher.ENCRYPT_MODE,
            key,
            GCMParameterSpec(TAG_LENGTH, iv)
        )

        val encrypted = cipher.doFinal(payload)

        return iv + encrypted
    }

    // =========================================================
    // AES-GCM DECRYPTION WITH COUNTER
    // =========================================================

    /**
     * Authenticates and decrypts the payload before returning its counter.
     * Authentication failures throw an exception.
     */
    fun decryptWithCounter(
        ciphertext: ByteArray,
        keyBytes: ByteArray
    ): DecryptedPayload {
        require(keyBytes.size == AES_KEY_LENGTH) {
            "AES key must be exactly $AES_KEY_LENGTH bytes"
        }

        require(
            ciphertext.size >= IV_LENGTH + COUNTER_LENGTH + 16
        ) {
            "Encrypted payload is too short"
        }

        val iv = ciphertext.copyOfRange(0, IV_LENGTH)
        val encryptedPayload = ciphertext.copyOfRange(
            IV_LENGTH,
            ciphertext.size
        )

        val key = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(AES_MODE)

        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(TAG_LENGTH, iv)
        )

        // doFinal verifies the GCM authentication tag.
        val decrypted = cipher.doFinal(encryptedPayload)

        require(decrypted.size >= COUNTER_LENGTH) {
            "Decrypted payload does not contain a counter"
        }

        val counter = ByteBuffer
            .wrap(decrypted, 0, COUNTER_LENGTH)
            .long

        require(counter > 0L) {
            "Invalid encryption counter"
        }

        return DecryptedPayload(
            counter = counter,
            plaintext = decrypted.copyOfRange(
                COUNTER_LENGTH,
                decrypted.size
            )
        )
    }

    /**
     * Compatibility method for callers that need plaintext only.
     */
    fun decrypt(
        ciphertext: ByteArray,
        keyBytes: ByteArray
    ): ByteArray {
        return decryptWithCounter(ciphertext, keyBytes).plaintext
    }

    // =========================================================
    // DIFFIE-HELLMAN KEY EXCHANGE
    // =========================================================

    fun generateDHKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("DH")
        keyPairGenerator.initialize(2048)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Computes the shared secret and derives a 128-bit AES key using SHA-256.
     *
     * Both peers must use this same derivation method.
     */
    fun computeSharedSecret(
        myPrivateKey: PrivateKey,
        peerPublicKeyBytes: ByteArray
    ): ByteArray {
        require(peerPublicKeyBytes.isNotEmpty()) {
            "Peer public key cannot be empty"
        }

        val keyFactory = KeyFactory.getInstance("DH")
        val publicKeySpec = X509EncodedKeySpec(peerPublicKeyBytes)
        val peerPublicKey = keyFactory.generatePublic(publicKeySpec)

        val keyAgreement = KeyAgreement.getInstance("DH")
        keyAgreement.init(myPrivateKey)
        keyAgreement.doPhase(peerPublicKey, true)

        val rawSecret = keyAgreement.generateSecret()

        // Hash the shared secret before deriving the AES key.
        val derivedKey = MessageDigest
            .getInstance("SHA-256")
            .digest(rawSecret)
            .copyOf(AES_KEY_LENGTH)

        rawSecret.fill(0)

        return derivedKey
    }
}
