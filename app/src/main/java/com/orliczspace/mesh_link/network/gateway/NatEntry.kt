package com.orliczspace.mesh_link.network.gateway

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class NatEntry(
    val srcIp: String,
    val srcPort: Int,
    val destIp: String,
    val destPort: Int,
    val payload: ByteArray
) {
    companion object {

        fun createFromIpPacket(raw: ByteArray): NatEntry {
            val buffer = ByteBuffer.wrap(raw)
            buffer.order(ByteOrder.BIG_ENDIAN)

            /* ---------------- IPv4 HEADER ---------------- */

            val versionAndIhl = buffer.get(0).toInt() and 0xFF
            val version = versionAndIhl shr 4
            require(version == 4) { "Not IPv4 packet" }

            val ihl = (versionAndIhl and 0x0F) * 4
            val protocol = buffer.get(9).toInt() and 0xFF
            require(protocol == 17) { "Not UDP packet" }

            val srcIpBytes = ByteArray(4)
            val destIpBytes = ByteArray(4)

            buffer.position(12)
            buffer.get(srcIpBytes)
            buffer.get(destIpBytes)

            val srcIp = InetAddress.getByAddress(srcIpBytes).hostAddress
            val destIp = InetAddress.getByAddress(destIpBytes).hostAddress

            /* ---------------- UDP HEADER ---------------- */

            buffer.position(ihl)

            val srcPort = buffer.short.toInt() and 0xFFFF
            val destPort = buffer.short.toInt() and 0xFFFF
            val udpLength = buffer.short.toInt() and 0xFFFF

            // Skip checksum
            buffer.short

            /* ---------------- PAYLOAD ---------------- */

            val payloadLength = udpLength - 8
            require(payloadLength >= 0) { "Invalid UDP length" }

            val payload = ByteArray(payloadLength)
            buffer.get(payload)

            return NatEntry(
                srcIp = srcIp,
                srcPort = srcPort,
                destIp = destIp,
                destPort = destPort,
                payload = payload
            )
        }
    }
}
