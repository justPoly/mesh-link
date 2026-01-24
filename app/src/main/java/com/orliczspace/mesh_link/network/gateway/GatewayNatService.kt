package com.orliczspace.mesh_link.network.gateway

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class GatewayNatService(
    private val flowLogger: FlowLogger,
    private val onInboundPacket: (ByteArray) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val socket = DatagramSocket()

    private val activeFlows = ConcurrentHashMap<String, NatEntry>()

    fun handleOutbound(rawIpPacket: ByteArray, sourceNodeId: String) {
        val natEntry = NatEntry.createFromIpPacket(rawIpPacket, sourceNodeId)
        activeFlows[sourceNodeId] = natEntry

        // 🔥 Hybrid logging begins here
        flowLogger.onFlowStarted(natEntry)

        scope.launch {
            try {
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

                val rebuilt = IpUdpPacketBuilder.buildResponse(
                    natEntry,
                    response.data.copyOf(response.length)
                )

                onInboundPacket(rebuilt)

                activeFlows.remove(sourceNodeId)
                flowLogger.onFlowEnded(natEntry)

            } catch (e: Exception) {
                Log.e("GatewayNatService", "NAT error", e)
                activeFlows.remove(sourceNodeId)
                flowLogger.onFlowEnded(natEntry)
            }
        }
    }

    fun getActiveFlows(): Map<String, NatEntry> = activeFlows.toMap()

    fun stop() {
        scope.cancel()
        socket.close()
        activeFlows.clear()
    }
}
