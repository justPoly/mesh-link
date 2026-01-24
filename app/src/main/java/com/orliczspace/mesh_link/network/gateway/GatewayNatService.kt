package com.orliczspace.mesh_link.network.gateway

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class GatewayNatService(
    private val flowLogger: IFlowLogger,
    private val onInboundPacket: (ByteArray) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val socket = DatagramSocket()

    /**
     * Active NAT flows:
     * sourceNodeId -> (NatEntry + lastActivityTime)
     */
    private val activeFlows =
        ConcurrentHashMap<String, Pair<NatEntry, Long>>()

    private val NAT_TIMEOUT_MS = 30_000L          // 30 seconds
    private val CLEANUP_INTERVAL_MS = 5_000L      // 5 seconds

    init {
        startTimeoutScheduler()
    }

    /**
     * Handle outbound internet-bound packet
     */
    fun handleOutbound(rawIpPacket: ByteArray, sourceNodeId: String?) {
        val natEntry = NatEntry.createFromIpPacket(rawIpPacket, sourceNodeId)
        val nodeKey = sourceNodeId ?: natEntry.sourceNodeId ?: return

        activeFlows[nodeKey] = natEntry to System.currentTimeMillis()
        flowLogger.logOutboundFlow(nodeKey, natEntry)

        scope.launch {
            try {
                // Send to real internet destination
                val outgoing = DatagramPacket(
                    natEntry.payload,
                    natEntry.payload.size,
                    InetAddress.getByName(natEntry.destIp),
                    natEntry.destPort
                )
                socket.send(outgoing)

                // Wait for response
                val buffer = ByteArray(65535)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)

                val responsePayload = response.data.copyOf(response.length)

                // Update activity timestamp
                activeFlows[nodeKey]?.let {
                    activeFlows[nodeKey] = it.first to System.currentTimeMillis()
                }

                // Rebuild packet back to VPN / mesh
                val rebuiltPacket =
                    IpUdpPacketBuilder.buildResponse(natEntry, responsePayload)

                onInboundPacket(rebuiltPacket)

                // Flow completed
                activeFlows.remove(nodeKey)
                flowLogger.markFlowEnded(nodeKey)

            } catch (e: Exception) {
                Log.e("GatewayNatService", "NAT error for node $nodeKey", e)
                activeFlows.remove(nodeKey)
                flowLogger.markFlowEnded(nodeKey)
            }
        }
    }

    /**
     * Periodically removes expired NAT flows
     */
    private fun startTimeoutScheduler() {
        scope.launch {
            while (isActive) {
                delay(CLEANUP_INTERVAL_MS)

                val now = System.currentTimeMillis()
                val expiredKeys = mutableListOf<String>()

                for ((nodeId, pair) in activeFlows) {
                    val lastSeen = pair.second
                    if (now - lastSeen > NAT_TIMEOUT_MS) {
                        expiredKeys.add(nodeId)
                    }
                }

                for (nodeId in expiredKeys) {
                    Log.d("GatewayNatService", "NAT flow timed out for node $nodeId")
                    activeFlows.remove(nodeId)
                    flowLogger.markFlowEnded(nodeId)
                }
            }
        }
    }

    /**
     * Snapshot of active NAT flows (without timestamps)
     */
    fun getActiveFlows(): Map<String, NatEntry> =
        activeFlows.mapValues { it.value.first }

    /**
     * Shutdown NAT service
     */
    fun stop() {
        scope.cancel()
        socket.close()
        activeFlows.clear()
    }
}
