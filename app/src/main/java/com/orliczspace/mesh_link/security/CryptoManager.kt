package com.orliczspace.mesh_link.network.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {

    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    // 🔑 DEMO KEY — replace with key exchange later
    private val key: SecretKey = SecretKeySpec(
        ByteArray(16) { 0x01 }, // placeholder
        "AES"
    )

    fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))

        val encrypted = cipher.doFinal(plain)

        return iv + encrypted
    }

    fun decrypt(ciphertext: ByteArray): ByteArray {
        val iv = ciphertext.copyOfRange(0, IV_LENGTH)
        val payload = ciphertext.copyOfRange(IV_LENGTH, ciphertext.size)

        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))

        return cipher.doFinal(payload)
    }
}
