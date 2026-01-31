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
 * Uses EWMA smoothing for RTT to stabilize routing decisions.
 */
class LinkProbeService(
    private val localNodeId: String,
    private val listenPort: Int = 8888,
    private val smoothingAlpha: Double = 0.2 // EWMA alpha
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null

    /** Smoothed RTT per neighbour (nodeId → EWMA RTT in ms) */
    private val rttEwma = ConcurrentHashMap<String, Double>()

    /** Raw RTT samples (for optional stats) */
    private val rttHistory = ConcurrentHashMap<String, MutableList<Long>>()

    /** Active probe timestamps (sequence → sendTime) */
    private val pendingProbes = ConcurrentHashMap<Long, Long>()

    /** Known peers discovered via probes (nodeId → IP) */
    private val knownPeers = ConcurrentHashMap<String, String>()

    private var sequenceCounter = 0L

    /** Callback when probe response received (adaptive scheduler) */
    var onProbeResponse: ((String) -> Unit)? = null

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

    /* ---------------- API ---------------- */

    fun getKnownPeers(): Map<String, String> = knownPeers.toMap()

    /** Returns EWMA-smoothed RTT in ms */
    fun getAverageRtt(neighbourId: String): Long? =
        rttEwma[neighbourId]?.toLong()

    /** Measures RTT stability (variance of raw samples) */
    fun getStability(neighbourId: String): Double {
        val history = rttHistory[neighbourId] ?: return 0.0
        val avg = history.average()
        return history.map { abs(it - avg) }.average()
    }

    /* ---------------- PROBE SEND ---------------- */

    fun sendProbe(targetAddress: InetAddress, targetPort: Int) {
        val seq = sequenceCounter++
        val timestamp = System.currentTimeMillis()

        val message = ProbeMessage(
            type = ProbeMessage.Type.REQUEST,
            senderId = localNodeId,
            sequence = seq,
            timestamp = timestamp,
            capabilities = getCapabilities()
        )

        pendingProbes[seq] = timestamp

        scope.launch {
            try {
                val data = ProbeCodec.encode(message)
                socket?.send(DatagramPacket(data, data.size, targetAddress, targetPort))
            } catch (e: Exception) {
                Log.e("LinkProbeService", "Send error: ${e.message}")
            }
        }
    }

    /* ---------------- LISTENING ---------------- */

    private fun startListening() {
        scope.launch {
            val buffer = ByteArray(1024)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    handleMessage(packet.data, packet.length, packet.address, packet.port)
                } catch (e: Exception) {
                    if (isActive) Log.e("LinkProbeService", "Receive error: ${e.message}")
                }
            }
        }
    }

    private fun handleMessage(data: ByteArray, length: Int, address: InetAddress, port: Int) {
        val message = try {
            ProbeCodec.decode(data, length)
        } catch (e: Exception) {
            Log.e("LinkProbeService", "Invalid probe packet")
            return
        }

        when (message.type) {
            ProbeMessage.Type.REQUEST -> {
                registerPeer(message.senderId, address.hostAddress, message.capabilities)
                sendProbeResponse(message, address, port)
            }

            ProbeMessage.Type.RESPONSE -> {
                registerPeer(message.senderId, address.hostAddress, message.capabilities)
                recordRtt(message.senderId, message.timestamp, System.currentTimeMillis())
                pendingProbes.remove(message.sequence)
                onProbeResponse?.invoke(message.senderId)
            }
        }
    }

    /* ---------------- RESPONSE ---------------- */

    private fun sendProbeResponse(request: ProbeMessage, address: InetAddress, port: Int) {
        val response = ProbeMessage(
            type = ProbeMessage.Type.RESPONSE,
            senderId = localNodeId,
            sequence = request.sequence,
            timestamp = request.timestamp,
            capabilities = getCapabilities()
        )

        scope.launch {
            try {
                val data = ProbeCodec.encode(response)
                socket?.send(DatagramPacket(data, data.size, address, port))
            } catch (e: Exception) {
                Log.e("LinkProbeService", "Response error: ${e.message}")
            }
        }
    }

    /* ---------------- PEER REGISTRATION ---------------- */

    private fun registerPeer(nodeId: String, ip: String, capabilities: Capabilities?) {
        val existing = knownPeers[nodeId]
        if (existing == null || existing != ip) {
            knownPeers[nodeId] = ip
            Log.d("LinkProbeService", "Peer registered: $nodeId @ $ip | $capabilities")
        }
    }

    /* ---------------- RTT RECORD ---------------- */

    private fun recordRtt(neighbourId: String, requestTime: Long, responseTime: Long) {
        val rtt = responseTime - requestTime

        // Update raw history
        val history = rttHistory.getOrPut(neighbourId) { mutableListOf() }
        history.add(rtt)
        if (history.size > 20) history.removeAt(0)

        // EWMA smoothing
        val oldEwma = rttEwma[neighbourId] ?: rtt.toDouble()
        val newEwma = smoothingAlpha * rtt + (1 - smoothingAlpha) * oldEwma
        rttEwma[neighbourId] = newEwma

        Log.d("LinkProbeService", "RTT $neighbourId = ${rtt}ms (EWMA=${newEwma.toInt()}ms)")
    }

    private fun getCapabilities(): Capabilities = Capabilities(hasInternet = false)
}
