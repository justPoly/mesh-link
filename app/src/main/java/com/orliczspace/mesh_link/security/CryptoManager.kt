package com.orliczspace.mesh_link.network.security

import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.KeyFactory

object CryptoManager {

    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    private val random = SecureRandom()

    /** ---------------- AES-GCM ---------------- */

    fun encrypt(plain: ByteArray, keyBytes: ByteArray, counter: Long = 0L): ByteArray {
        val iv = ByteArray(IV_LENGTH)
        random.nextBytes(iv)

        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
        val payload = counterBytes + plain

        val key = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))

        val encrypted = cipher.doFinal(payload)
        return iv + encrypted
    }

    fun decrypt(ciphertext: ByteArray, keyBytes: ByteArray): ByteArray {
        val iv = ciphertext.copyOfRange(0, IV_LENGTH)
        val payload = ciphertext.copyOfRange(IV_LENGTH, ciphertext.size)

        val key = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))

        val decrypted = cipher.doFinal(payload)
        // remove counter
        return decrypted.copyOfRange(8, decrypted.size)
    }

    /** ---------------- Replay counter helper ---------------- */

    fun extractCounter(ciphertext: ByteArray): Long {
        val iv = ciphertext.copyOfRange(0, IV_LENGTH)
        val payload = ciphertext.copyOfRange(IV_LENGTH, ciphertext.size)

        val decrypted = Cipher.getInstance(AES_MODE).let { cipher ->
            val key = SecretKeySpec(ByteArray(16) { 0x01 }, "AES") // placeholder
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))
            cipher.doFinal(payload)
        }
        return ByteBuffer.wrap(decrypted.copyOfRange(0, 8)).long
    }

    /** ---------------- Diffie-Hellman Key Exchange ---------------- */

    fun generateDHKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("DH")
        kpg.initialize(2048)
        return kpg.generateKeyPair()
    }

    fun computeSharedSecret(myPrivateKey: java.security.PrivateKey, peerPublicKeyBytes: ByteArray): ByteArray {
        val kf = KeyFactory.getInstance("DH")
        val pubSpec = X509EncodedKeySpec(peerPublicKeyBytes)
        val peerPublicKey = kf.generatePublic(pubSpec)

        val ka = KeyAgreement.getInstance("DH")
        ka.init(myPrivateKey)
        ka.doPhase(peerPublicKey, true)
        val rawSecret = ka.generateSecret()

        // Use first 16 bytes as AES key
        return rawSecret.copyOf(16)
    }
}
