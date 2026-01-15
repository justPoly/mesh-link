package com.orliczspace.mesh_link.network.gateway

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.inv

object IpUdpPacketBuilder {

    fun buildResponse(
        nat: NatEntry,
        responsePayload: ByteArray
    ): ByteArray {

        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val totalLen = ipHeaderLen + udpHeaderLen + responsePayload.size

        val buffer = ByteBuffer.allocate(totalLen)
        buffer.order(ByteOrder.BIG_ENDIAN)

        /* ---------------- IPv4 HEADER ---------------- */

        buffer.put(0x45.toByte()) // Version 4, IHL 5
        buffer.put(0x00)          // DSCP/ECN
        buffer.putShort(totalLen.toShort())
        buffer.putShort(0)        // Identification
        buffer.putShort(0x4000.toShort()) // Flags + Fragment offset
        buffer.put(64.toByte())   // TTL
        buffer.put(17.toByte())   // Protocol UDP
        buffer.putShort(0)        // Checksum (temporary)

        buffer.put(InetAddress.getByName(nat.destIp).address)
        buffer.put(InetAddress.getByName(nat.srcIp).address)

        val ipChecksum = checksum(buffer.array(), 0, ipHeaderLen)
        buffer.putShort(10, ipChecksum)

        /* ---------------- UDP HEADER ---------------- */

        buffer.position(ipHeaderLen)

        buffer.putShort(nat.destPort.toShort())
        buffer.putShort(nat.srcPort.toShort())
        buffer.putShort((udpHeaderLen + responsePayload.size).toShort())
        buffer.putShort(0) // UDP checksum optional (0 allowed)

        buffer.put(responsePayload)

        return buffer.array()
    }

    private fun checksum(
        data: ByteArray,
        offset: Int,
        length: Int
    ): Short {
        var sum = 0
        var i = offset

        while (i < offset + length) {
            val word =
                ((data[i].toInt() shl 8) and 0xFF00) +
                        (data[i + 1].toInt() and 0xFF)

            sum += word
            i += 2
        }

        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        return sum.inv().toShort()
    }
}
