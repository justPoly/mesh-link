package com.orliczspace.mesh_link.network.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PacketCryptoManager {

    private val secureRandom = SecureRandom()

    fun encrypt(
        plaintext: ByteArray,
        key: SecretKey
    ): EncryptedPayload {
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)

        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val ciphertext = cipher.doFinal(plaintext)

        return EncryptedPayload(
            iv = iv,
            ciphertext = ciphertext
        )
    }

    fun decrypt(
        encrypted: EncryptedPayload,
        key: SecretKey
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, encrypted.iv)

        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(encrypted.ciphertext)
    }
}

data class EncryptedPayload(
    val iv: ByteArray,
    val ciphertext: ByteArray
)
