package com.orliczspace.mesh_link.network.gateway

import android.util.Log
import com.orliczspace.mesh_link.network.NetworkActivityLog
import com.orliczspace.mesh_link.network.packet.MeshPacket
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

/**
 * Stateful UDP NAT service for mesh gateway nodes.
 * Tracks outbound flows and allows NAT-forwarded packets to be handled.
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

    /** NAT table: FlowKey -> NAT data */
    private val activeFlows =
        ConcurrentHashMap<NatFlowKey, NatFlowData>()

    init {
        startTimeoutCleaner()
    }

    /** Handle outbound internet-bound packet via NAT */
    fun handleOutbound(
        rawIpPacket: ByteArray,
        sourceNodeId: String?
    ) {
        if (sourceNodeId == null) return

        val natEntry =
            NatEntry.createFromIpPacket(
                rawIpPacket,
                sourceNodeId
            )

        val flowKey =
            NatFlowKey(
                nodeId = sourceNodeId,
                srcIp = natEntry.srcIp,
                srcPort = natEntry.srcPort,
                destIp = natEntry.destIp,
                destPort = natEntry.destPort
            )

        val metrics =
            GatewayNatFlowMetrics(
                startTime = System.currentTimeMillis()
            )

        activeFlows[flowKey] =
            NatFlowData(
                entry = natEntry,
                metrics = metrics
            )

        NetworkActivityLog.record(
            message = "Network flow started",
            detail = "${natEntry.destIp}:${natEntry.destPort}"
        )

        flowLogger.logOutboundFlow(
            sourceNodeId,
            natEntry
        )

        scope.launch {

            try {

                val rtt =
                    measureTimeMillis {

                        val outgoing =
                            DatagramPacket(
                                natEntry.payload,
                                natEntry.payload.size,
                                InetAddress.getByName(
                                    natEntry.destIp
                                ),
                                natEntry.destPort
                            )

                        socket.send(outgoing)

                        val buffer =
                            ByteArray(65535)

                        val response =
                            DatagramPacket(
                                buffer,
                                buffer.size
                            )

                        socket.receive(response)

                        val responsePayload =
                            response.data.copyOf(
                                response.length
                            )

                        val rebuiltPacket =
                            IpUdpPacketBuilder.buildResponse(
                                natEntry,
                                responsePayload
                            )

                        onInboundPacket(
                            rebuiltPacket
                        )
                    }

                metrics.rttMs = rtt
                metrics.success = true

                NetworkActivityLog.record(
                    message = "Network flow completed",
                    detail =
                        "${natEntry.destIp}:${natEntry.destPort} • ${rtt}ms"
                )

            } catch (e: Exception) {

                metrics.success = false

                NetworkActivityLog.record(
                    message = "Network flow failed",
                    detail =
                        "${natEntry.destIp}:${natEntry.destPort}"
                )

                Log.e(
                    "GatewayNatService",
                    "NAT error for flow $flowKey",
                    e
                )

            } finally {

                activeFlows.remove(
                    flowKey
                )

                flowLogger.markFlowEnded(
                    sourceNodeId
                )
            }
        }
    }

    /**
     * Handle inbound NAT-forwarded packet from
     * the internet or another gateway.
     */
    fun handleInbound(
        data: ByteArray
    ) {
        val packet =
            MeshPacket.deserialize(
                data,
                data.size
            )

        onInboundPacket(
            packet.serialize()
        )
    }

    /** Periodically remove expired NAT flows */
    private fun startTimeoutCleaner() {

        scope.launch {

            while (isActive) {

                delay(5_000)

                val now =
                    System.currentTimeMillis()

                val expiredKeys =
                    activeFlows
                        .filter {
                            now - it.value.metrics.startTime >
                                    NAT_TIMEOUT_MS
                        }
                        .keys

                expiredKeys.forEach { key ->

                    val removed =
                        activeFlows.remove(key)

                    if (removed != null) {

                        NetworkActivityLog.record(
                            message = "Network flow expired",
                            detail =
                                "${removed.entry.destIp}:${removed.entry.destPort}"
                        )

                        flowLogger.markFlowEnded(
                            key.nodeId
                        )

                        Log.d(
                            "GatewayNatService",
                            "Expired NAT flow: $key"
                        )
                    }
                }
            }
        }
    }

    /**
     * Snapshot of active flows.
     *
     * Note:
     * This groups flows by source node ID.
     * Use getActiveFlowCount() when the exact
     * number of active NAT flows is required.
     */
    fun getActiveFlows(): Map<String, NatEntry> =
        activeFlows
            .mapKeys {
                it.key.nodeId
            }
            .mapValues {
                it.value.entry
            }

    /**
     * Returns the exact number of currently
     * active NAT flows.
     */
    fun getActiveFlowCount(): Int =
        activeFlows.size

    /** Stop NAT service */
    fun stop() {
        scope.cancel()
        socket.close()
        activeFlows.clear()
    }
}

/** NAT table data container */
data class NatFlowData(
    val entry: NatEntry,
    val metrics: GatewayNatFlowMetrics
)

/** Per-flow metrics for NAT */
data class GatewayNatFlowMetrics(
    val startTime: Long,
    var rttMs: Long = 0,
    var success: Boolean = false
)