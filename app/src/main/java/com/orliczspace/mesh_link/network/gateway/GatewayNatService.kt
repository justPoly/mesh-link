package com.orliczspace.mesh_link.network.gateway

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Stateful UDP NAT service for mesh gateway nodes.
 * Uses 5-tuple NAT flow tracking with timeout-based expiration.
 */
class GatewayNatService(
    private val flowLogger: IFlowLogger,
    private val onInboundPacket: (ByteArray) -> Unit
) {

    companion object {
        private const val NAT_TIMEOUT_MS = 30_000L // 30 seconds
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val socket = DatagramSocket()

    /**
     * NAT table:
     * NatFlowKey -> Pair<NatEntry, lastSeenTimestamp>
     */
    private val activeFlows =
        ConcurrentHashMap<NatFlowKey, Pair<NatEntry, Long>>()

    init {
        startTimeoutCleaner()
    }

    /**
     * Handle outbound internet-bound UDP packet.
     */
    fun handleOutbound(rawIpPacket: ByteArray, sourceNodeId: String?) {
        if (sourceNodeId == null) return

        val natEntry = NatEntry.createFromIpPacket(rawIpPacket, sourceNodeId)

        val flowKey = NatFlowKey(
            nodeId = sourceNodeId,
            srcIp = natEntry.srcIp,
            srcPort = natEntry.srcPort,
            destIp = natEntry.destIp,
            destPort = natEntry.destPort
        )

        activeFlows[flowKey] = natEntry to System.currentTimeMillis()
        flowLogger.logOutboundFlow(sourceNodeId, natEntry)

        scope.launch {
            try {
                // Send packet to real internet destination
                val outgoing = DatagramPacket(
                    natEntry.payload,
                    natEntry.payload.size,
                    InetAddress.getByName(natEntry.destIp),
                    natEntry.destPort
                )
                socket.send(outgoing)

                // Receive response (UDP)
                val buffer = ByteArray(65535)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)

                val responsePayload = response.data.copyOf(response.length)

                // Refresh flow activity
                activeFlows[flowKey] =
                    natEntry to System.currentTimeMillis()

                // Rebuild IP/UDP packet for VPN/TUN
                val rebuiltPacket =
                    IpUdpPacketBuilder.buildResponse(natEntry, responsePayload)

                onInboundPacket(rebuiltPacket)

            } catch (e: Exception) {
                Log.e(
                    "GatewayNatService",
                    "NAT error for flow $flowKey",
                    e
                )
            }
        }
    }

    /**
     * Periodically remove expired NAT flows.
     */
    private fun startTimeoutCleaner() {
        scope.launch {
            while (isActive) {
                delay(5_000)

                val now = System.currentTimeMillis()
                val expiredKeys = mutableListOf<NatFlowKey>()

                for ((key, value) in activeFlows) {
                    val lastSeen = value.second
                    if (now - lastSeen > NAT_TIMEOUT_MS) {
                        expiredKeys.add(key)
                    }
                }

                for (key in expiredKeys) {
                    activeFlows.remove(key)
                    flowLogger.markFlowEnded(key.nodeId)
                    Log.d(
                        "GatewayNatService",
                        "Expired NAT flow: $key"
                    )
                }
            }
        }
    }

    /**
     * Snapshot of active NAT flows (for UI / debugging).
     */
    fun getActiveFlows(): List<NatEntry> {
        return activeFlows.values.map { it.first }
    }

    /**
     * Stop NAT service.
     */
    fun stop() {
        scope.cancel()
        socket.close()
        activeFlows.clear()
    }
}
