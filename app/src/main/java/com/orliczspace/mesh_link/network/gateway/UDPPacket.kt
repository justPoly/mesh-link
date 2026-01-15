package com.orliczspace.mesh_link.network.gateway

import java.nio.ByteBuffer

data class UDPPacket(
    val srcPort: Int,
    val dstPort: Int,
    val payload: ByteArray
) {
    companion object {
        fun parse(raw: ByteArray): UDPPacket {
            val buffer = ByteBuffer.wrap(raw)
            val srcPort = buffer.short.toInt() and 0xFFFF
            val dstPort = buffer.short.toInt() and 0xFFFF
            buffer.short // length (ignored)
            buffer.short // checksum (ignored)
            val payload = ByteArray(buffer.remaining())
            buffer.get(payload)
            return UDPPacket(srcPort, dstPort, payload)
        }

        fun build(srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
            val buffer = ByteBuffer.allocate(8 + payload.size)
            buffer.putShort(srcPort.toShort())
            buffer.putShort(dstPort.toShort())
            buffer.putShort((8 + payload.size).toShort()) // length
            buffer.putShort(0) // checksum placeholder
            buffer.put(payload)
            return buffer.array()
        }
    }
}
