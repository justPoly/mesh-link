package com.orliczspace.mesh_link.network.gateway

data class NatEntry(
    val destIp: String,
    val destPort: Int,
    val payload: ByteArray
) {
    companion object {
        fun createFromIpPacket(raw: ByteArray): NatEntry {
            // Placeholder parser (real IPv4 parsing comes next)
            return NatEntry(
                destIp = "8.8.8.8",
                destPort = 53,
                payload = raw
            )
        }
    }
}
