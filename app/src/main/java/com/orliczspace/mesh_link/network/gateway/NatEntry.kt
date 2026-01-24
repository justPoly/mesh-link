package com.orliczspace.mesh_link.network.gateway

/**
 * Represents a single NAT flow entry for a UDP packet.
 * Used for tracking outbound internet traffic and rebuilding return packets.
 */
data class NatEntry(
    val srcIp: String,
    val srcPort: Int,
    val destIp: String,
    val destPort: Int,
    val payload: ByteArray,
    val sourceNodeId: String? = null
) {
    companion object {

        /**
         * Parse a raw IPv4 + UDP packet into a NatEntry.
         * Only UDP is supported.
         */
        fun createFromIpPacket(
            raw: ByteArray,
            sourceNodeId: String? = null
        ): NatEntry {
            val ip = IPv4Packet.parse(raw)

            if (ip.protocol != 17) {
                throw IllegalArgumentException("Only UDP packets are supported for NAT")
            }

            val udp = UDPPacket.parse(ip.payload)

            return NatEntry(
                srcIp = ip.srcIp,
                srcPort = udp.srcPort,
                destIp = ip.dstIp,
                destPort = udp.dstPort, //
                payload = udp.payload,
                sourceNodeId = sourceNodeId
            )
        }
    }
}
