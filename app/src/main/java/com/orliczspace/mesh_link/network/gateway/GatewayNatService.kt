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

    companion object {
        private const val NAT_TIMEOUT_MS = 30_000L
        private const val CLEANUP_INTERVAL_MS = 5_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val socket = DatagramSocket()

    /**
     * NAT table:
     * FlowKey → (NatEntry, NatFlowMetrics)
     */
    private val activeFlows =
        ConcurrentHashMap<NatFlowKey, Pair<NatEntry, NatFlowMetrics>>()

    init {
        startTimeoutCleaner()
    }

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

        val metrics = activeFlows[flowKey]?.second ?: NatFlowMetrics(
            nodeId = sourceNodeId,
            srcIp = natEntry.srcIp,
            srcPort = natEntry.srcPort,
            destIp = natEntry.destIp,
            destPort = natEntry.destPort
        )

        metrics.recordOutbound(natEntry.payload.size)
        activeFlows[flowKey] = natEntry to metrics
        flowLogger.logOutboundFlow(sourceNodeId, natEntry)

        scope.launch {
            try {
                val sendTime = System.currentTimeMillis()

                val outgoing = DatagramPacket(
                    natEntry.payload,
                    natEntry.payload.size,
                    InetAddress.getByName(natEntry.destIp),
                    natEntry.destPort
                )
                socket.send(outgoing)

                val buffer = ByteArray(65535)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)

                val recvTime = System.currentTimeMillis()
                val rtt = recvTime - sendTime

                val responsePayload = response.data.copyOf(response.length)
                metrics.recordInbound(responsePayload.size, rtt)

                activeFlows[flowKey] = natEntry to metrics

                val rebuiltPacket =
                    IpUdpPacketBuilder.buildResponse(natEntry, responsePayload)

                onInboundPacket(rebuiltPacket)

            } catch (e: Exception) {
                Log.e("GatewayNatService", "NAT error for flow $flowKey", e)
            }
        }
    }

    private fun startTimeoutCleaner() {
        scope.launch {
            while (isActive) {
                delay(CLEANUP_INTERVAL_MS)

                val now = System.currentTimeMillis()
                val expired = mutableListOf<NatFlowKey>()

                for ((key, pair) in activeFlows) {
                    val metrics = pair.second
                    if (now - metrics.lastSeen > NAT_TIMEOUT_MS) {
                        expired.add(key)
                    }
                }

                for (key in expired) {
                    val (_, metrics) = activeFlows.remove(key) ?: continue
                    flowLogger.persistMetrics(metrics)
                    flowLogger.markFlowEnded(key.nodeId)

                    Log.d(
                        "GatewayNatService",
                        "Expired flow ${key.nodeId} RTT avg=${metrics.averageRtt()}ms"
                    )
                }
            }
        }
    }

    fun getActiveFlows(): List<NatEntry> =
        activeFlows.values.map { it.first }

    fun stop() {
        scope.cancel()
        socket.close()
        activeFlows.clear()
    }
}
