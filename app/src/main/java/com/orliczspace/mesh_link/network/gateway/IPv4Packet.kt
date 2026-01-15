package com.orliczspace.mesh_link.network.gateway

import java.nio.ByteBuffer
import java.net.InetAddress

data class IPv4Packet(
    val version: Int,
    val ihl: Int,
    val ttl: Int,
    val protocol: Int,
    val srcIp: String,
    val dstIp: String,
    val payload: ByteArray
) {
    companion object {
        fun parse(raw: ByteArray): IPv4Packet {
            val buffer = ByteBuffer.wrap(raw)
            val first = buffer.get().toInt()
            val version = (first shr 4) and 0xF
            val ihl = first and 0xF
            buffer.get() // DSCP + ECN
            val totalLength = buffer.short.toInt() and 0xFFFF
            buffer.short // Identification
            buffer.short // Flags + Fragment offset
            val ttl = buffer.get().toInt() and 0xFF
            val protocol = buffer.get().toInt() and 0xFF
            buffer.short // Header checksum
            val src = ByteArray(4)
            buffer.get(src)
            val dst = ByteArray(4)
            buffer.get(dst)
            val headerLength = ihl * 4
            val payloadLength = totalLength - headerLength
            val payload = ByteArray(payloadLength)
            buffer.get(payload)
            return IPv4Packet(
                version, ihl, ttl, protocol,
                InetAddress.getByAddress(src).hostAddress,
                InetAddress.getByAddress(dst).hostAddress,
                payload
            )
        }
    }

    fun toBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(ihl * 4 + payload.size)
        buffer.put(((version shl 4) or ihl).toByte())
        buffer.put(0) // DSCP + ECN
        buffer.putShort((ihl * 4 + payload.size).toShort()) // Total length
        buffer.putShort(0) // Identification
        buffer.putShort(0) // Flags + Fragment
        buffer.put(ttl.toByte())
        buffer.put(protocol.toByte())
        buffer.putShort(0) // checksum placeholder
        buffer.put(InetAddress.getByName(srcIp).address)
        buffer.put(InetAddress.getByName(dstIp).address)
        buffer.put(payload)
        // TODO: optionally calculate IPv4 checksum
        return buffer.array()
    }
}
