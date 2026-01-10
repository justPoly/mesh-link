package com.orliczspace.mesh_link.network.ip

import java.net.InetAddress
import java.nio.ByteBuffer

data class IpPacket(
    val sourceIp: InetAddress,
    val destinationIp: InetAddress,
    val protocol: Int,
    val payload: ByteArray
)

object IpPacketParser {

    fun parse(packet: ByteArray): IpPacket? {
        if (packet.size < 20) return null

        val buffer = ByteBuffer.wrap(packet)

        val versionIhl = buffer.get(0).toInt()
        val ihl = (versionIhl and 0x0F) * 4

        if (packet.size < ihl) return null

        val protocol = buffer.get(9).toInt() and 0xFF

        val src = InetAddress.getByAddress(packet.copyOfRange(12, 16))
        val dst = InetAddress.getByAddress(packet.copyOfRange(16, 20))

        val payload = packet.copyOfRange(ihl, packet.size)

        return IpPacket(
            sourceIp = src,
            destinationIp = dst,
            protocol = protocol,
            payload = payload
        )
    }
}
