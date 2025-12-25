package com.orliczspace.mesh_link.network

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Probe-based link quality measurement service.
 */
class LinkProbeService(
    private val localNodeId: String,
    private val listenPort: Int = 8888
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null

    private val rttHistory = ConcurrentHashMap<String, MutableList<Long>>()
    private val pendingProbes = ConcurrentHashMap<Long, Long>()

    private var sequenceCounter = 0L

    fun start() {
        socket = DatagramSocket(listenPort)
        startListening()
        Log.d("LinkProbeService", "Started on port $listenPort")
    }

    fun stop() {
        scope.cancel()
        socket?.close()
        Log.d("LinkProbeService", "Stopped")
    }

    fun sendProbe(targetAddress: InetAddress, targetPort: Int) {
        val seq = sequenceCounter++
        val timestamp = System.currentTimeMillis()

        val message = "PROBE_REQ|$localNodeId|$seq|$timestamp"
        val data = message.toByteArray()

        pendingProbes[seq] = timestamp

        scope.launch {
            try {
                socket?.send(DatagramPacket(data, data.size, targetAddress, targetPort))
            } catch (e: Exception) {
                Log.e("LinkProbeService", "Send error: ${e.message}")
            }
        }
    }

    private fun startListening() {
        scope.launch {
            val buffer = ByteArray(1024)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    handleMessage(
                        String(packet.data, 0, packet.length),
                        packet.address,
                        packet.port
                    )
                } catch (e: Exception) {
                    if (isActive) Log.e("LinkProbeService", "Receive error: ${e.message}")
                }
            }
        }
    }

    private fun handleMessage(message: String, address: InetAddress, port: Int) {
        val parts = message.split("|")

        when (parts[0]) {
            "PROBE_REQ" -> {
                val seq = parts[2].toLong()
                val requestTime = parts[3].toLong()
                sendProbeResponse(seq, requestTime, address, port)
            }

            "PROBE_RES" -> {
                val responderId = parts[1]
                val requestTime = parts[3].toLong()
                val responseTime = parts[4].toLong()
                recordRtt(responderId, requestTime, responseTime)
            }
        }
    }

    private fun sendProbeResponse(
        seq: Long,
        requestTime: Long,
        address: InetAddress,
        port: Int
    ) {
        val responseTime = System.currentTimeMillis()
        val message = "PROBE_RES|$localNodeId|$seq|$requestTime|$responseTime"

        scope.launch {
            try {
                socket?.send(
                    DatagramPacket(message.toByteArray(), message.length, address, port)
                )
            } catch (e: Exception) {
                Log.e("LinkProbeService", "Response error: ${e.message}")
            }
        }
    }

    private fun recordRtt(
        neighbourId: String,
        requestTime: Long,
        responseTime: Long
    ) {
        val rtt = responseTime - requestTime
        val history = rttHistory.getOrPut(neighbourId) { mutableListOf() }
        history.add(rtt)
        if (history.size > 20) history.removeAt(0)

        Log.d("LinkProbeService", "RTT $neighbourId = ${rtt}ms")
    }

    fun getAverageRtt(neighbourId: String): Long? =
        rttHistory[neighbourId]?.average()?.toLong()

    fun getStability(neighbourId: String): Double {
        val history = rttHistory[neighbourId] ?: return 0.0
        val avg = history.average()
        return history.map { abs(it - avg) }.average()
    }
}
