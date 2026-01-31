package com.orliczspace.mesh_link.security

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.random.Random

object CryptoManager {

    private const val AES_KEY_SIZE = 256
    private const val GCM_IV_SIZE = 12
    private const val GCM_TAG_SIZE = 128

    // 🔑 TEMP: shared mesh key (Phase 1)
    private val secretKey: SecretKey by lazy {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(AES_KEY_SIZE)
        keyGen.generateKey()
    }

    fun encrypt(data: ByteArray): ByteArray {
        val iv = Random.nextBytes(GCM_IV_SIZE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            secretKey,
            GCMParameterSpec(GCM_TAG_SIZE, iv)
        )

        val encrypted = cipher.doFinal(data)

        // IV + ciphertext
        return iv + encrypted
    }

    fun decrypt(data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, GCM_IV_SIZE)
        val ciphertext = data.copyOfRange(GCM_IV_SIZE, data.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            GCMParameterSpec(GCM_TAG_SIZE, iv)
        )

        return cipher.doFinal(ciphertext)
    }
}
