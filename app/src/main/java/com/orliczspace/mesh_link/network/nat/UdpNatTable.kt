package com.orliczspace.mesh_link.network.nat

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

data class UdpNatKey(
    val externalIp: InetAddress,
    val externalPort: Int,
    val protocol: Int = 17 // UDP
)

data class UdpNatEntry(
    val internalIp: InetAddress,
    val internalPort: Int,
    val sourceNodeId: String,
    var lastSeen: Long = System.currentTimeMillis()
)

class UdpNatTable(
    private val timeoutMs: Long = 60_000
) {

    private val table = ConcurrentHashMap<UdpNatKey, UdpNatEntry>()

    fun put(
        externalIp: InetAddress,
        externalPort: Int,
        internalIp: InetAddress,
        internalPort: Int,
        sourceNodeId: String
    ) {
        val key = UdpNatKey(externalIp, externalPort)
        table[key] = UdpNatEntry(
            internalIp = internalIp,
            internalPort = internalPort,
            sourceNodeId = sourceNodeId
        )
    }

    fun lookup(
        externalIp: InetAddress,
        externalPort: Int
    ): UdpNatEntry? {
        val key = UdpNatKey(externalIp, externalPort)
        return table[key]?.also {
            it.lastSeen = System.currentTimeMillis()
        }
    }

    fun cleanup() {
        val now = System.currentTimeMillis()
        table.entries.removeIf {
            now - it.value.lastSeen > timeoutMs
        }
    }
}
