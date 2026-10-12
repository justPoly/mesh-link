
package com.orliczspace.mesh_link.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Probe-based link quality and identity discovery service.
 *
 * Discovers peers on the local IP subnet using UDP broadcast.
 * Uses EWMA smoothing for RTT to stabilize routing decisions.
 */
class LinkProbeService(
    private val localNodeId: String,
    private val listenPort: Int = 8888,
    private val smoothingAlpha: Double = 0.2
) {
    companion object {
        private const val TAG = "LinkProbeService"
        private const val DISCOVERY_INTERVAL_MS = 5_000L
        private const val PENDING_PROBE_TIMEOUT_MS = 30_000L
        private const val RECEIVE_BUFFER_SIZE = 1024
    }

    private val scope =
        CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var socket: DatagramSocket? = null

    @Volatile
    private var running = false

    private var discoveryJob: kotlinx.coroutines.Job? = null

    /** Smoothed RTT per neighbour, in milliseconds. */
    private val rttEwma = ConcurrentHashMap<String, Double>()

    /** Raw RTT samples used to estimate stability. */
    private val rttHistory =
        ConcurrentHashMap<String, MutableList<Long>>()

    /** Sequence number -> local send timestamp. */
    private val pendingProbes =
        ConcurrentHashMap<Long, Long>()

    /** Known peers: persistent node ID -> IP address. */
    private val knownPeers =
        ConcurrentHashMap<String, String>()

    private val sequenceCounter = AtomicLong(0L)

    /** Called when a matching probe response is received. */
    @Volatile
    var onProbeResponse: ((String) -> Unit)? = null

    /** Called when a peer's Node ID and IP address are learned. */
    @Volatile
    var onPeerDiscovered: ((nodeId: String, ipAddress: String) -> Unit)? = null

    @Synchronized
    fun start() {
        if (running && socket?.isClosed == false) {
            Log.d(TAG, "Already running on UDP port $listenPort")
            return
        }

        try {
            val newSocket = DatagramSocket(listenPort).apply {
                broadcast = true
            }

            socket = newSocket
            running = true

            startListening()
            startAutomaticDiscovery()

            Log.d(
                TAG,
                "Started on UDP port $listenPort; automatic discovery enabled"
            )
        } catch (e: Exception) {
            running = false
            socket?.close()
            socket = null
            Log.e(TAG, "Could not start probe service", e)
            throw e
        }
    }

    @Synchronized
    fun stop() {
        running = false

        discoveryJob?.cancel()
        discoveryJob = null

        socket?.close()
        socket = null

        scope.cancel()

        pendingProbes.clear()

        Log.d(TAG, "Stopped")
    }

    // ---------------------------------------------------------
    // Public API
    // ---------------------------------------------------------

    fun getKnownPeers(): Map<String, String> =
        knownPeers.toMap()

    fun getAverageRtt(neighbourId: String): Long? =
        rttEwma[neighbourId]?.toLong()

    fun getStability(neighbourId: String): Double {
        val history = synchronized(rttHistory) {
            rttHistory[neighbourId]?.toList()
        } ?: return 0.0

        if (history.isEmpty()) return 0.0

        val average = history.average()

        return history
            .map { abs(it - average) }
            .average()
    }

    // ---------------------------------------------------------
    // Automatic LAN discovery
    // ---------------------------------------------------------

    private fun startAutomaticDiscovery() {
        discoveryJob = scope.launch {
            while (isActive && running) {
                removeExpiredProbes()
                broadcastDiscoveryProbe()
                delay(DISCOVERY_INTERVAL_MS)
            }
        }
    }

    private fun broadcastDiscoveryProbe() {
        val activeSocket = socket ?: return

        if (activeSocket.isClosed) return

        val broadcastAddresses = try {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .filter { networkInterface ->
                    try {
                        networkInterface.isUp &&
                                !networkInterface.isLoopback
                    } catch (_: Exception) {
                        false
                    }
                }
                .flatMap { networkInterface ->
                    networkInterface.interfaceAddresses
                        .mapNotNull { it.broadcast }
                }
                .filterNot {
                    it.isLoopbackAddress || it.isAnyLocalAddress
                }
                .distinct()
        } catch (e: Exception) {
            Log.e(TAG, "Could not enumerate network interfaces", e)
            emptyList()
        }

        if (broadcastAddresses.isEmpty()) {
            Log.w(
                TAG,
                "No subnet broadcast address found; " +
                        "check the Wi-Fi connection and interface permissions"
            )
            return
        }

        for (address in broadcastAddresses) {
            sendProbe(address, listenPort)
        }

        Log.d(
            TAG,
            "Discovery probes sent to ${broadcastAddresses.joinToString()}"
        )
    }

    // ---------------------------------------------------------
    // Probe sending
    // ---------------------------------------------------------

    fun sendProbe(targetAddress: InetAddress, targetPort: Int) {
        if (!running) return
        if (targetPort !in 1..65535) return

        val activeSocket = socket ?: return
        if (activeSocket.isClosed) return

        val sequence = sequenceCounter.getAndIncrement()
        val timestamp = System.currentTimeMillis()

        val message = ProbeMessage(
            type = ProbeMessage.Type.REQUEST,
            senderId = localNodeId,
            sequence = sequence,
            timestamp = timestamp,
            capabilities = getCapabilities()
        )

        pendingProbes[sequence] = timestamp

        scope.launch {
            try {
                val data = ProbeCodec.encode(message)

                activeSocket.send(
                    DatagramPacket(
                        data,
                        data.size,
                        targetAddress,
                        targetPort
                    )
                )

                Log.d(
                    TAG,
                    "Probe $sequence sent to " +
                            "${targetAddress.hostAddress}:$targetPort"
                )
            } catch (e: Exception) {
                if (running) {
                    Log.e(TAG, "Probe send failed", e)
                }
            }
        }
    }

    // ---------------------------------------------------------
    // Listening
    // ---------------------------------------------------------

    private fun startListening() {
        val activeSocket = socket ?: return

        scope.launch {
            val buffer = ByteArray(RECEIVE_BUFFER_SIZE)

            Log.d(TAG, "Listening for probes on UDP $listenPort")

            while (isActive && running && !activeSocket.isClosed) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)

                    activeSocket.receive(packet)

                    handleMessage(
                        data = packet.data,
                        length = packet.length,
                        address = packet.address,
                        port = packet.port
                    )
                } catch (e: Exception) {
                    if (isActive && running && !activeSocket.isClosed) {
                        Log.e(TAG, "Probe receive error", e)
                    }
                }
            }

            Log.d(TAG, "Probe listener stopped")
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
            Log.w(TAG, "Rejected invalid probe packet", e)
            return
        }

        if (message.senderId.isBlank() ||
            message.senderId == localNodeId
        ) {
            Log.w(TAG, "Ignoring invalid or self-sent probe")
            return
        }

        when (message.type) {
            ProbeMessage.Type.REQUEST -> {
                registerPeer(
                    nodeId = message.senderId,
                    ip = address.hostAddress ?: return,
                    capabilities = message.capabilities
                )

                sendProbeResponse(
                    request = message,
                    address = address,
                    port = port
                )
            }

            ProbeMessage.Type.RESPONSE -> {
                registerPeer(
                    nodeId = message.senderId,
                    ip = address.hostAddress ?: return,
                    capabilities = message.capabilities
                )

                /*
                 * Keep the timestamp temporarily so multiple peers can
                 * respond to the same subnet broadcast sequence number.
                 * Expired entries are removed by removeExpiredProbes().
                 */
                val sentAt = pendingProbes[message.sequence]

                if (sentAt != null) {
                    recordRtt(
                        neighbourId = message.senderId,
                        requestTime = sentAt,
                        responseTime = System.currentTimeMillis()
                    )

                    onProbeResponse?.invoke(message.senderId)
                } else {
                    Log.d(
                        TAG,
                        "Response without a matching probe: " +
                                message.senderId
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------
    // Probe response
    // ---------------------------------------------------------

    private fun sendProbeResponse(
        request: ProbeMessage,
        address: InetAddress,
        port: Int
    ) {
        val activeSocket = socket ?: return
        if (activeSocket.isClosed) return

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

                activeSocket.send(
                    DatagramPacket(
                        data,
                        data.size,
                        address,
                        port
                    )
                )

                Log.d(
                    TAG,
                    "Response sent to ${address.hostAddress}:$port"
                )
            } catch (e: Exception) {
                if (running) {
                    Log.e(TAG, "Probe response failed", e)
                }
            }
        }
    }

    // ---------------------------------------------------------
    // Peer registration
    // ---------------------------------------------------------

    private fun registerPeer(
        nodeId: String,
        ip: String,
        capabilities: Capabilities?
    ) {
        if (nodeId.isBlank() || nodeId == localNodeId) return

        val existing = knownPeers.put(nodeId, ip)

        if (existing == null) {
            NetworkActivityLog.record(
                message = "Node connected",
                detail = "$nodeId @ $ip"
            )

            Log.d(
                TAG,
                "Peer discovered: $nodeId @ $ip | $capabilities"
            )
        } else if (existing != ip) {
            NetworkActivityLog.record(
                message = "Node address changed",
                detail = "$nodeId → $ip"
            )

            Log.d(
                TAG,
                "Peer address updated: $nodeId @ $ip"
            )
        }

        onPeerDiscovered?.invoke(nodeId, ip)
    }

    // ---------------------------------------------------------
    // RTT statistics
    // ---------------------------------------------------------

    private fun recordRtt(
        neighbourId: String,
        requestTime: Long,
        responseTime: Long
    ) {
        val rtt = (responseTime - requestTime).coerceAtLeast(0L)

        val history = synchronized(rttHistory) {
            rttHistory.getOrPut(neighbourId) {
                mutableListOf()
            }.apply {
                add(rtt)
                if (size > 20) removeAt(0)
            }.toList()
        }

        val oldEwma = rttEwma[neighbourId] ?: rtt.toDouble()

        val newEwma =
            smoothingAlpha * rtt +
                    (1.0 - smoothingAlpha) * oldEwma

        rttEwma[neighbourId] = newEwma

        Log.d(
            TAG,
            "RTT $neighbourId = ${rtt}ms " +
                    "(EWMA=${newEwma.toLong()}ms; samples=${history.size})"
        )
    }

    private fun removeExpiredProbes() {
        val cutoff =
            System.currentTimeMillis() - PENDING_PROBE_TIMEOUT_MS

        pendingProbes.entries.removeIf { entry ->
            entry.value < cutoff
        }
    }

    private fun getCapabilities(): Capabilities =
        Capabilities(hasInternet = false)
}
