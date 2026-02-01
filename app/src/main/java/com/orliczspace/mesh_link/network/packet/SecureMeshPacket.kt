package com.orliczspace.mesh_link.network.packet

import java.nio.ByteBuffer

data class SecureMeshPacket(
    val iv: ByteArray,
    val ciphertext: ByteArray
) {
    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(4 + iv.size + ciphertext.size)
        buffer.putInt(iv.size)
        buffer.put(iv)
        buffer.put(ciphertext)
        return buffer.array()
    }

    companion object {
        fun fromByteArray(bytes: ByteArray): SecureMeshPacket {
            val buffer = ByteBuffer.wrap(bytes)
            val ivLength = buffer.int

            val iv = ByteArray(ivLength)
            buffer.get(iv)

            val ciphertext = ByteArray(buffer.remaining())
            buffer.get(ciphertext)

            return SecureMeshPacket(iv, ciphertext)
        }
    }
}
