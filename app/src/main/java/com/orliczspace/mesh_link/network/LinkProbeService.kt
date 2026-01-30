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
 * Maintains smoothed RTT per neighbor (EWMA) for adaptive routing.
 */
class LinkProbeService(
    private val localNodeId: String,
    private val listenPort: Int = 8888
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null

    /* ---------------- STATE ---------------- */

    // Smoothed RTT per neighbour (nodeId → EWMA RTT in ms)
    private val smoothedRtt = ConcurrentHashMap<String, Double>()

    // Raw RTT history per neighbour (for stability calculation)
    private val rttHistory = ConcurrentHashMap<String, MutableList<Long>>()

    // Pending probe timestamps (seq → sendTime)
    private val pendingProbes = ConcurrentHashMap<Long, Long>()

    // Known peers discovered via probes (nodeId → IP)
    private val knownPeers = ConcurrentHashMap<String, String>()

    private var sequenceCounter = 0L

    // Callback for adaptive probe scheduler
    var onProbeResponse: ((String) -> Unit)? = null

    companion object {
        private const val EWMA_ALPHA = 0.2  // smoothing factor
        private const val MAX_HISTORY = 20   // raw history size
    }

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

    fun getKnownPeers(): Map<String, String> = knownPeers.toMap()

    /** Smoothed RTT in ms for this neighbor */
    fun getAverageRtt(neighbourId: String): Long? =
        smoothedRtt[neighbourId]?.toLong()

    /** Stability metric (average absolute deviation from smoothed RTT) */
    fun getStability(neighbourId: String): Double {
        val history = rttHistory[neighbourId] ?: return 0.0
        val avgRtt = smoothedRtt[neighbourId] ?: history.average()
        return history.map { abs(it - avgRtt) }.average()
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
                socket?.send(
                    DatagramPacket(
                        data,
                        data.size,
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
                    handleMessage(packet.data, packet.length, packet.address, packet.port)
                } catch (e: Exception) {
                    if (isActive) Log.e("LinkProbeService", "Receive error: ${e.message}")
                }
            }
        }
    }

    private fun handleMessage(
        data: ByteArray,
        length: Int,
        address: InetAddress,
        port: Int
    ) {
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
                onProbeResponse?.invoke(message.senderId)
                pendingProbes.remove(message.sequence)
            }
        }
    }

    /* ---------------- RESPONSE SEND ---------------- */

    private fun sendProbeResponse(
        request: ProbeMessage,
        address: InetAddress,
        port: Int
    ) {
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

    /* ---------------- METRICS ---------------- */

    private fun recordRtt(neighbourId: String, requestTime: Long, responseTime: Long) {
        val rtt = responseTime - requestTime
        val history = rttHistory.getOrPut(neighbourId) { mutableListOf() }

        // Update raw history
        history.add(rtt)
        if (history.size > MAX_HISTORY) history.removeAt(0)

        // EWMA smoothing
        val prev = smoothedRtt[neighbourId] ?: rtt.toDouble()
        val newSmoothed = EWMA_ALPHA * rtt + (1 - EWMA_ALPHA) * prev
        smoothedRtt[neighbourId] = newSmoothed

        Log.d("LinkProbeService", "RTT $neighbourId = ${rtt}ms, smoothed = ${newSmoothed.format(1)}ms")
    }

    /* ---------------- CAPABILITIES ---------------- */
    private fun getCapabilities(): Capabilities {
        return Capabilities(hasInternet = false)
    }

    /* ---------------- HELPERS ---------------- */
    private fun Double.format(digits: Int) = "%.${digits}f".format(this)
}
