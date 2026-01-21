package com.orliczspace.mesh_link.network.gateway

data class NatEntry(
    val srcIp: String,
    val srcPort: Int,
    val destIp: String,
    val destPort: Int,
    val payload: ByteArray,
    val sourceNodeId: String? = null // NEW: track originating node
) {
    companion object {
        fun createFromIpPacket(raw: ByteArray, sourceNodeId: String? = null): NatEntry {
            val ip = IPv4Packet.parse(raw)
            if (ip.protocol != 17) throw IllegalArgumentException("Only UDP supported")
            val udp = UDPPacket.parse(ip.payload)
            return NatEntry(
                srcIp = ip.srcIp,
                srcPort = udp.srcPort,
                destIp = ip.dstIp,
                destPort = udp.dstPort,
                payload = udp.payload,
                sourceNodeId = sourceNodeId
            )
        }
    }
}
