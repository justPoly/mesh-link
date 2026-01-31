package com.orliczspace.mesh_link.network

import com.orliczspace.mesh_link.network.packet.MeshPacket
import com.orliczspace.mesh_link.network.packet.PacketType
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object ForwardPacketCodec {

    fun encode(packet: MeshPacket): ByteArray {
        val src = packet.sourceNodeId.toByteArray(StandardCharsets.UTF_8)
        val dst = packet.destinationNodeId?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        val payload = packet.payload

        val buffer = ByteBuffer.allocate(
            4 + src.size +
                    4 + dst.size +
                    4 + payload.size +
                    1 + 1 + 1
        )

        buffer.putInt(src.size)
        buffer.put(src)

        buffer.putInt(dst.size)
        buffer.put(dst)

        buffer.putInt(payload.size)
        buffer.put(payload)

        buffer.put(packet.type.ordinal.toByte())
        buffer.put(packet.ttl.toByte())
        buffer.put(if (packet.requiresAck) 1 else 0)

        return buffer.array()
    }

    fun decode(bytes: ByteArray): MeshPacket {
        val buffer = ByteBuffer.wrap(bytes)

        val srcLen = buffer.int
        val src = ByteArray(srcLen)
        buffer.get(src)

        val dstLen = buffer.int
        val dst = ByteArray(dstLen)
        buffer.get(dst)

        val payloadLen = buffer.int
        val payload = ByteArray(payloadLen)
        buffer.get(payload)

        val type = PacketType.values()[buffer.get().toInt()]
        val ttl = buffer.get().toInt()
        val ack = buffer.get().toInt() == 1

        return MeshPacket(
            sourceNodeId = String(src),
            destinationNodeId = if (dst.isEmpty()) null else String(dst),
            payload = payload,
            type = type,
            ttl = ttl,
            requiresAck = ack
        )
    }
}
