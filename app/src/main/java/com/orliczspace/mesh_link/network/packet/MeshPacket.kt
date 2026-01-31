package com.orliczspace.mesh_link.network.packet

import java.nio.ByteBuffer
import java.util.*

data class MeshPacket(
    val packetId: String = UUID.randomUUID().toString(),
    val sourceNodeId: String,
    val destinationNodeId: String?,
    val payload: ByteArray,
    val ttl: Int = 8,
    val type: PacketType,
    val requiresAck: Boolean = false,
    val ackForPacketId: String? = null
) {
    /** Serialize packet into byte array (simple stub; you can improve encoding later) */
    fun serialize(): ByteArray {
        val idBytes = packetId.toByteArray()
        val sourceBytes = sourceNodeId.toByteArray()
        val destBytes = destinationNodeId?.toByteArray() ?: ByteArray(0)
        val payloadSize = payload.size
        val buffer = ByteBuffer.allocate(
            4 + idBytes.size + 4 + sourceBytes.size + 4 + destBytes.size + 4 + payloadSize + 1
        )
        buffer.putInt(idBytes.size)
        buffer.put(idBytes)
        buffer.putInt(sourceBytes.size)
        buffer.put(sourceBytes)
        buffer.putInt(destBytes.size)
        buffer.put(destBytes)
        buffer.putInt(payloadSize)
        buffer.put(payload)
        buffer.put(type.ordinal.toByte())
        return buffer.array()
    }

    companion object {
        /** Deserialize byte array into MeshPacket */
        fun deserialize(data: ByteArray, length: Int): MeshPacket {
            val buffer = ByteBuffer.wrap(data, 0, length)

            val idLen = buffer.int
            val idBytes = ByteArray(idLen)
            buffer.get(idBytes)
            val packetId = String(idBytes)

            val sourceLen = buffer.int
            val sourceBytes = ByteArray(sourceLen)
            buffer.get(sourceBytes)
            val sourceNodeId = String(sourceBytes)

            val destLen = buffer.int
            val destBytes = ByteArray(destLen)
            buffer.get(destBytes)
            val destinationNodeId = if (destLen > 0) String(destBytes) else null

            val payloadLen = buffer.int
            val payloadBytes = ByteArray(payloadLen)
            buffer.get(payloadBytes)

            val typeByte = buffer.get().toInt()
            val type = PacketType.values()[typeByte]

            return MeshPacket(
                packetId = packetId,
                sourceNodeId = sourceNodeId,
                destinationNodeId = destinationNodeId,
                payload = payloadBytes,
                type = type
            )
        }

        /** Create an ACK packet */
        fun createAck(sourceNodeId: String, packetId: String): MeshPacket {
            return MeshPacket(
                sourceNodeId = sourceNodeId,
                destinationNodeId = "unknown",
                payload = ByteArray(0),
                type = PacketType.ACK,
                requiresAck = false,
                ackForPacketId = packetId
            )
        }
    }
}
