package com.orliczspace.mesh_link.network

import java.nio.ByteBuffer

object ForwardPacketCodec {

    fun encode(packet: ForwardPacket): ByteArray {
        val sourceBytes = packet.sourceNodeId.toByteArray()
        val destBytes = packet.destinationNodeId.toByteArray()

        val buffer = ByteBuffer.allocate(
            4 + sourceBytes.size +
                    4 + destBytes.size +
                    4 +
                    4 + packet.payload.size
        )

        buffer.putInt(sourceBytes.size)
        buffer.put(sourceBytes)

        buffer.putInt(destBytes.size)
        buffer.put(destBytes)

        buffer.putInt(packet.ttl)

        buffer.putInt(packet.payload.size)
        buffer.put(packet.payload)

        return buffer.array()
    }

    fun decode(data: ByteArray, length: Int): ForwardPacket {
        val buffer = ByteBuffer.wrap(data, 0, length)

        val sourceLen = buffer.int
        val source = ByteArray(sourceLen)
        buffer.get(source)

        val destLen = buffer.int
        val dest = ByteArray(destLen)
        buffer.get(dest)

        val ttl = buffer.int

        val payloadLen = buffer.int
        val payload = ByteArray(payloadLen)
        buffer.get(payload)

        return ForwardPacket(
            sourceNodeId = String(source),
            destinationNodeId = String(dest),
            ttl = ttl,
            payload = payload
        )
    }
}
