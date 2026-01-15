package com.orliczspace.mesh_link.network.gateway

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.inv

object IpUdpPacketBuilder {

    /**
     * Build a return IPv4 + UDP packet based on the NAT entry and the response payload.
     *
     * This swaps source/destination IPs and ports, recalculates IPv4 checksum,
     * and returns a full packet ready to send back to the VPN.
     */
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
        buffer.putShort(totalLen.toShort()) // Total Length
        buffer.putShort(0)        // Identification
        buffer.putShort(0x4000.toShort()) // Flags + Fragment offset (Don't fragment)
        buffer.put(64.toByte())   // TTL
        buffer.put(17.toByte())   // Protocol UDP
        buffer.putShort(0)        // Checksum placeholder

        // Swap source/destination IP
        buffer.put(InetAddress.getByName(nat.destIp).address)
        buffer.put(InetAddress.getByName(nat.srcIp).address)

        // Compute IPv4 checksum
        val ipChecksum = checksum(buffer.array(), 0, ipHeaderLen)
        buffer.putShort(10, ipChecksum)

        /* ---------------- UDP HEADER ---------------- */

        buffer.position(ipHeaderLen)
        buffer.putShort(nat.destPort.toShort())
        buffer.putShort(nat.srcPort.toShort())
        buffer.putShort((udpHeaderLen + responsePayload.size).toShort()) // UDP length
        buffer.putShort(0) // UDP checksum optional (0 = no checksum)

        /* ---------------- UDP PAYLOAD ---------------- */

        buffer.put(responsePayload)

        return buffer.array()
    }

    /**
     * Standard IPv4 header checksum computation.
     */
    private fun checksum(
        data: ByteArray,
        offset: Int,
        length: Int
    ): Short {
        var sum = 0
        var i = offset

        while (i < offset + length) {
            val word =
                ((data[i].toInt() and 0xFF) shl 8) +
                        (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }

        // Fold 32-bit sum to 16 bits
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        return sum.inv().toShort()
    }
}
