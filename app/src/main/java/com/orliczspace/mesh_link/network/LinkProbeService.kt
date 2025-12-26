package com.orliczspace.mesh_link.network

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Probe-based link quality + identity discovery service.
 * Works ONLY with real IP-layer connectivity.
 */
class LinkProbeService(
    private val localNodeId: String,
    private val listenPort: Int = 8888
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null

    /* ---------------- STATE ---------------- */

    // RTT history per neighbour (nodeId → RTT samples)
    private val rttHistory = ConcurrentHashMap<String, MutableList<Long>>()

    // Active probe timestamps (seq → sendTime)
    private val pendingProbes = ConcurrentHashMap<Long, Long>()

    // Known peers discovered via probes (nodeId → IP)
    private val knownPeers = ConcurrentHashMap<String, String>()

    private var sequenceCounter = 0L

    /* ---------------- LIFECYCLE ---------------- */

    fun start() {
        socket = DatagramSocket(listenPort)
        startListening()
        Log.d("LinkProbeService", "Started on UDP port $listenPort")
    }

    fun stop() {
        scope.cancel()
        socket?.close()
        Log.d("LinkProbeService", "Stopped")
    }

    /* ---------------- EXTERNAL API ---------------- */

    fun getKnownPeers(): Map<String, String> =
        knownPeers.toMap()

    fun getAverageRtt(neighbourId: String): Long? =
        rttHistory[neighbourId]?.average()?.toLong()

    fun getStability(neighbourId: String): Double {
        val history = rttHistory[neighbourId] ?: return 0.0
        val avg = history.average()
        return history.map { abs(it - avg) }.average()
    }

    /* ---------------- PROBE SEND ---------------- */

    fun sendProbe(targetAddress: InetAddress, targetPort: Int) {
        val seq = sequenceCounter++
        val timestamp = System.currentTimeMillis()

        val message =
            "PROBE_REQ|$localNodeId|$seq|$timestamp|${getCapabilities()}"

        pendingProbes[seq] = timestamp

        scope.launch {
            try {
                socket?.send(
                    DatagramPacket(
                        message.toByteArray(),
                        message.length,
                        targetAddress,
                        targetPort
                    )
                )
            } catch (e: Exception) {
                Log.e("LinkProbeService", "Send error: ${e.message}")
            }
        }
    }

    /* ---------------- RECEIVE ---------------- */

    private fun startListening() {
        scope.launch {
            val buffer = ByteArray(1024)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)

                    handleMessage(
                        message = String(packet.data, 0, packet.length),
                        address = packet.address,
                        port = packet.port
                    )

                } catch (e: Exception) {
                    if (isActive) {
                        Log.e("LinkProbeService", "Receive error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun handleMessage(
        message: String,
        address: InetAddress,
        port: Int
    ) {
        val parts = message.split("|")

        if (parts.size < 5) return

        when (parts[0]) {

            /* ---------- PROBE REQUEST ---------- */

            "PROBE_REQ" -> {
                val senderNodeId = parts[1]
                val seq = parts[2].toLong()
                val requestTime = parts[3].toLong()
                val capabilities = parts[4]

                registerPeer(senderNodeId, address.hostAddress, capabilities)

                sendProbeResponse(seq, requestTime, address, port)
            }

            /* ---------- PROBE RESPONSE ---------- */

            "PROBE_RES" -> {
                if (parts.size < 6) return

                val responderNodeId = parts[1]
                val seq = parts[2].toLong()
                val requestTime = parts[3].toLong()
                val responseTime = parts[4].toLong()
                val capabilities = parts[5]

                registerPeer(responderNodeId, address.hostAddress, capabilities)
                recordRtt(responderNodeId, requestTime, responseTime)

                pendingProbes.remove(seq)
            }
        }
    }

    /* ---------------- RESPONSE SEND ---------------- */

    private fun sendProbeResponse(
        seq: Long,
        requestTime: Long,
        address: InetAddress,
        port: Int
    ) {
        val responseTime = System.currentTimeMillis()

        val message =
            "PROBE_RES|$localNodeId|$seq|$requestTime|$responseTime|${getCapabilities()}"

        scope.launch {
            try {
                socket?.send(
                    DatagramPacket(
                        message.toByteArray(),
                        message.length,
                        address,
                        port
                    )
                )
            } catch (e: Exception) {
                Log.e("LinkProbeService", "Response error: ${e.message}")
            }
        }
    }

    /* ---------------- PEER REGISTRATION ---------------- */

    private fun registerPeer(
        nodeId: String,
        ip: String,
        capabilities: String
    ) {
        val existing = knownPeers[nodeId]

        if (existing == null || existing != ip) {
            knownPeers[nodeId] = ip
            Log.d(
                "LinkProbeService",
                "Peer registered: $nodeId @ $ip | $capabilities"
            )
        }
    }

    /* ---------------- METRICS ---------------- */

    private fun recordRtt(
        neighbourId: String,
        requestTime: Long,
        responseTime: Long
    ) {
        val rtt = responseTime - requestTime
        val history =
            rttHistory.getOrPut(neighbourId) { mutableListOf() }

        history.add(rtt)
        if (history.size > 20) history.removeAt(0)

        Log.d("LinkProbeService", "RTT $neighbourId = ${rtt}ms")
    }

    /* ---------------- CAPABILITIES ---------------- */

    private fun getCapabilities(): String {
        // Future: INTERNET:1;BATTERY:80;RELAY:1
        return "INTERNET:0"
    }
}
