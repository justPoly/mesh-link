
package com.orliczspace.mesh_link.network.packet

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

data class MeshPacket(
    val packetId: String = UUID.randomUUID().toString(),
    val sourceNodeId: String,
    val destinationNodeId: String?,
    val payload: ByteArray,
    val ttl: Int = 8,
    val type: PacketType,
    val seqNumber: Long = 0L,
    val requiresAck: Boolean = false,
    val encrypted: Boolean = false,
    val ackForPacketId: String? = null
) {

    companion object {
        private const val MAGIC = 0x4D53484C // "MSHL"
        private const val VERSION = 1
        private const val MAX_PACKET_SIZE = 65_507
        private const val MAX_STRING_LENGTH = 4_096

        fun deserialize(data: ByteArray, length: Int): MeshPacket {
            require(length in 1..data.size) {
                "Invalid packet length: $length"
            }
            require(length <= MAX_PACKET_SIZE) {
                "Packet exceeds maximum UDP payload size"
            }

            val input = DataInputStream(
                ByteArrayInputStream(data, 0, length)
            )

            try {
                require(input.readInt() == MAGIC) {
                    "Invalid mesh packet signature"
                }

                require(input.readUnsignedByte() == VERSION) {
                    "Unsupported mesh packet version"
                }

                val packetId = input.readBoundedString()
                val sourceNodeId = input.readBoundedString()
                val destinationNodeId = input.readNullableString()

                val ttl = input.readInt()
                require(ttl in 0..255) {
                    "Invalid packet TTL"
                }

                val seqNumber = input.readLong()
                val requiresAck = input.readBoolean()
                val encrypted = input.readBoolean()
                val ackForPacketId = input.readNullableString()

                val typeOrdinal = input.readUnsignedByte()
                require(typeOrdinal < PacketType.entries.size) {
                    "Invalid packet type: $typeOrdinal"
                }

                val payloadLength = input.readInt()
                require(payloadLength >= 0 && payloadLength <= input.available()) {
                    "Invalid payload length: $payloadLength"
                }

                val payload = ByteArray(payloadLength)
                input.readFully(payload)

                require(input.available() == 0) {
                    "Unexpected trailing bytes in mesh packet"
                }

                return MeshPacket(
                    packetId = packetId,
                    sourceNodeId = sourceNodeId,
                    destinationNodeId = destinationNodeId,
                    payload = payload,
                    ttl = ttl,
                    type = PacketType.entries[typeOrdinal],
                    seqNumber = seqNumber,
                    requiresAck = requiresAck,
                    encrypted = encrypted,
                    ackForPacketId = ackForPacketId
                )
            } catch (e: IOException) {
                throw IllegalArgumentException(
                    "Malformed mesh packet",
                    e
                )
            }
        }

        fun createAck(
            sourceNodeId: String,
            packetId: String
        ): MeshPacket {
            return MeshPacket(
                sourceNodeId = sourceNodeId,
                destinationNodeId = null,
                payload = ByteArray(0),
                type = PacketType.ACK,
                requiresAck = false,
                ackForPacketId = packetId
            )
        }
    }

    fun serialize(): ByteArray {
        val outputBytes = ByteArrayOutputStream()

        DataOutputStream(outputBytes).use { output ->
            output.writeInt(MAGIC)
            output.writeByte(VERSION)

            output.writeBoundedString(packetId)
            output.writeBoundedString(sourceNodeId)
            output.writeNullableString(destinationNodeId)

            output.writeInt(ttl)
            output.writeLong(seqNumber)
            output.writeBoolean(requiresAck)
            output.writeBoolean(encrypted)
            output.writeNullableString(ackForPacketId)

            output.writeByte(type.ordinal)

            output.writeInt(payload.size)
            output.write(payload)
        }

        val result = outputBytes.toByteArray()

        require(result.size <= MAX_PACKET_SIZE) {
            "Serialized mesh packet exceeds maximum UDP payload size"
        }

        return result
    }
}

private fun DataOutputStream.writeBoundedString(value: String) {
    require(value.toByteArray(Charsets.UTF_8).size <= 4_096) {
        "Mesh packet string is too long"
    }
    writeUTF(value)
}

private fun DataOutputStream.writeNullableString(value: String?) {
    writeBoolean(value != null)
    if (value != null) {
        writeBoundedString(value)
    }
}

private fun DataInputStream.readBoundedString(): String {
    val value = readUTF()
    require(value.toByteArray(Charsets.UTF_8).size <= 4_096) {
        "Mesh packet string is too long"
    }
    return value
}

private fun DataInputStream.readNullableString(): String? {
    return if (readBoolean()) readBoundedString() else null
}
