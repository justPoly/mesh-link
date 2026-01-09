package com.orliczspace.mesh_link.network

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object ForwardPacketCodec {

    fun encode(packet: ForwardPacket): ByteArray {
        val sourceBytes = packet.sourceNodeId.toByteArray(StandardCharsets.UTF_8)
        val destBytes = packet.destinationNodeId
            ?.toByteArray(StandardCharsets.UTF_8)

        val hasDestination = destBytes != null

        val bufferSize =
            4 + sourceBytes.size +        // source length + source
                    1 +                            // hasDestination flag
                    (if (hasDestination) 4 + destBytes!!.size else 0) +
                    4 +                            // TTL
                    4 + packet.payload.size        // payload length + payload

        val buffer = ByteBuffer.allocate(bufferSize)

        // Source
        buffer.putInt(sourceBytes.size)
        buffer.put(sourceBytes)

        // Destination flag
        buffer.put(if (hasDestination) 1 else 0)

        // Destination (if present)
        if (hasDestination) {
            buffer.putInt(destBytes!!.size)
            buffer.put(destBytes)
        }

        // TTL
        buffer.putInt(packet.ttl)

        // Payload
        buffer.putInt(packet.payload.size)
        buffer.put(packet.payload)

        return buffer.array()
    }

    fun decode(data: ByteArray, length: Int): ForwardPacket {
        val buffer = ByteBuffer.wrap(data, 0, length)

        // Source
        val sourceLen = buffer.int
        val sourceBytes = ByteArray(sourceLen)
        buffer.get(sourceBytes)
        val sourceNodeId = String(sourceBytes, StandardCharsets.UTF_8)

        // Destination flag
        val hasDestination = buffer.get().toInt() == 1

        // Destination (nullable)
        val destinationNodeId =
            if (hasDestination) {
                val destLen = buffer.int
                val destBytes = ByteArray(destLen)
                buffer.get(destBytes)
                String(destBytes, StandardCharsets.UTF_8)
            } else {
                null
            }

        // TTL
        val ttl = buffer.int

        // Payload
        val payloadLen = buffer.int
        val payload = ByteArray(payloadLen)
        buffer.get(payload)

        return ForwardPacket(
            sourceNodeId = sourceNodeId,
            destinationNodeId = destinationNodeId,
            ttl = ttl,
            payload = payload
        )
    }
}
