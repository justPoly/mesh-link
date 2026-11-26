package com.orliczspace.mesh_link.network

import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
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

class LinkProbeService(
    private val localNodeId: String,
    private val listenPort: Int = 8888,
    private val smoothingAlpha: Double = 0.2,
    val peerRegistry: PeerRegistry = PeerRegistry(),
    private val internetCapabilityProvider: () -> Boolean = { false },
    private val meshPort: Int = 9999
) {

    companion object {
        private const val TAG = "LinkProbeService"

        private const val DISCOVERY_INTERVAL_MS = 5_000L
        private const val HEARTBEAT_INTERVAL_MS = 5_000L
        private const val PEER_TIMEOUT_MS = 20_000L
        private const val TIMEOUT_CHECK_INTERVAL_MS = 5_000L
        private const val PENDING_PROBE_TIMEOUT_MS = 10_000L

        private const val RECEIVE_BUFFER_SIZE = 2048
        private const val MAX_RTT_SAMPLES = 20
    }

    private data class PendingProbe(
        val sentAt: Long,
        val expectedAddress: InetAddress?,
        val expectedPort: Int,
        val expectedNodeId: String? = null,
        val responders: MutableSet<String> =
            ConcurrentHashMap.newKeySet<String>()
    )

    private data class ProbeEndpoint(
        val address: InetAddress,
        val port: Int
    )

    private var scope =
        CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val peerLock = Any()

    @Volatile
    private var socket: DatagramSocket? = null

    @Volatile
    private var running = false

    private var discoveryJob: Job? = null
    private var heartbeatJob: Job? = null
    private var timeoutJob: Job? = null

    private val rttEwma =
        ConcurrentHashMap<String, Double>()

    private val rttHistory =
        ConcurrentHashMap<String, MutableList<Long>>()

    private val pendingProbes =
        ConcurrentHashMap<Long, PendingProbe>()

    private val probeEndpoints =
        ConcurrentHashMap<String, ProbeEndpoint>()

    private val lastResponseTimes =
        ConcurrentHashMap<String, Long>()

    private val firstDiscoveryTimes =
        ConcurrentHashMap<String, Long>()

    private val sequenceCounter =
        AtomicLong(0L)

    @Volatile
    var onProbeResponse: ((String) -> Unit)? = null

    @Volatile
    var onPeerDiscovered:
            ((nodeId: String, ipAddress: String) -> Unit)? = null

    @Volatile
    var onPeerConnected: ((MeshPeer) -> Unit)? = null

    @Volatile
    var onPeerLost: ((String) -> Unit)? = null

    @Volatile
    var onPeerReconnected: ((MeshPeer) -> Unit)? = null

    // ---------------------------------------------------------
    // Service lifecycle
    // ---------------------------------------------------------

    @Synchronized
    fun start() {

        if (running && socket?.isClosed == false) {
            Log.d(TAG, "Service already running")
            return
        }

        scope =
            CoroutineScope(Dispatchers.IO + SupervisorJob())

        try {

            socket = DatagramSocket(listenPort).apply {
                broadcast = true
            }

            running = true

            startListening()
            startAutomaticDiscovery()
            startHeartbeat()
            startTimeoutMonitoring()

            Log.d(
                TAG,
                "Started on UDP port $listenPort"
            )

        } catch (e: Exception) {

            running = false

            socket?.close()
            socket = null

            scope.cancel()

            Log.e(TAG, "Service startup failed", e)

            throw e
        }
    }

    @Synchronized
    fun stop() {

        running = false

        discoveryJob?.cancel()
        heartbeatJob?.cancel()
        timeoutJob?.cancel()

        discoveryJob = null
        heartbeatJob = null
        timeoutJob = null

        socket?.close()
        socket = null

        scope.cancel()

        synchronized(peerLock) {

            peerRegistry.getAllPeers()
                .filter {
                    it.connectionState != PeerConnectionState.LOST
                }
                .forEach {
                    notifyCallback {
                        onPeerLost?.invoke(it.nodeId)
                    }
                }

            pendingProbes.clear()
            probeEndpoints.clear()
            lastResponseTimes.clear()
            firstDiscoveryTimes.clear()

            rttEwma.clear()

            synchronized(rttHistory) {
                rttHistory.clear()
            }

            peerRegistry.clear()
        }

        Log.d(TAG, "Service stopped")
    }

    // ---------------------------------------------------------
    // Public API
    // ---------------------------------------------------------

    fun getKnownPeers(): Map<String, String> {

        return peerRegistry.getAllPeers()
            .filter {
                it.connectionState != PeerConnectionState.LOST
            }
            .associate {
                it.nodeId to it.ipAddress
            }
    }

    fun getPeer(nodeId: String): MeshPeer? {
        return peerRegistry.getPeer(nodeId)
    }

    fun getAllPeers(): List<MeshPeer> {
        return peerRegistry.getAllPeers()
    }

    fun getConnectedPeers(): List<MeshPeer> {
        return peerRegistry.getConnectedPeers()
    }

    fun hasPeerInternetAccess(nodeId: String): Boolean {

        val peer = peerRegistry.getPeer(nodeId)

        return peer?.connectionState ==
                PeerConnectionState.CONNECTED &&
                peer.hasInternetAccess
    }

    fun getAverageRtt(neighbourId: String): Long? {
        return rttEwma[neighbourId]?.toLong()
    }

    fun getStability(neighbourId: String): Double {

        val history = synchronized(rttHistory) {
            rttHistory[neighbourId]?.toList()
        } ?: return 0.0

        if (history.isEmpty()) return 0.0

        val average = history.average()

        return history.map {
            abs(it - average)
        }.average()
    }

    fun requestDiscovery() {

        if (!running) {
            Log.w(TAG, "Discovery service is not running")
            return
        }

        scope.launch {
            Log.d(TAG, "Manual discovery requested")
            broadcastDiscoveryProbe()
        }
    }

    // ---------------------------------------------------------
    // Automatic discovery
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

        if (!running) return

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
                    it.isLoopbackAddress ||
                            it.isAnyLocalAddress
                }
                .distinct()

        } catch (e: Exception) {

            Log.e(TAG, "Interface enumeration failed", e)

            emptyList()
        }

        if (broadcastAddresses.isEmpty()) {

            Log.w(TAG, "No broadcast addresses available")

            return
        }

        broadcastAddresses.forEach { address ->

            sendProbeInternal(
                targetAddress = address,
                targetPort = listenPort,
                isBroadcast = true
            )
        }
    }

    // ---------------------------------------------------------
    // Peer heartbeat
    // ---------------------------------------------------------

    private fun startHeartbeat() {

        heartbeatJob = scope.launch {

            while (isActive && running) {

                peerRegistry.getAllPeers()
                    .filter {
                        it.connectionState !=
                                PeerConnectionState.LOST
                    }
                    .forEach { peer ->

                        val endpoint =
                            probeEndpoints[peer.nodeId]
                                ?: return@forEach

                        sendProbeInternal(
                            targetAddress = endpoint.address,
                            targetPort = endpoint.port,
                            isBroadcast = false,
                            expectedNodeId = peer.nodeId
                        )
                    }

                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    // ---------------------------------------------------------
    // Probe sending
    // ---------------------------------------------------------

    fun sendProbe(
        targetAddress: InetAddress,
        targetPort: Int
    ) {

        sendProbeInternal(
            targetAddress = targetAddress,
            targetPort = targetPort,
            isBroadcast = false
        )
    }

    private fun sendProbeInternal(
        targetAddress: InetAddress,
        targetPort: Int,
        isBroadcast: Boolean,
        expectedNodeId: String? = null
    ) {

        if (!running) return
        if (targetPort !in 1..65535) return

        val activeSocket = socket ?: return

        if (activeSocket.isClosed) return

        scope.launch {

            val sequence =
                sequenceCounter.getAndIncrement()

            val timestamp =
                System.currentTimeMillis()

            val message = ProbeMessage(
                type = ProbeMessage.Type.REQUEST,
                senderId = localNodeId,
                sequence = sequence,
                timestamp = timestamp,
                capabilities = getCapabilities()
            )

            pendingProbes[sequence] = PendingProbe(
                sentAt = SystemClock.elapsedRealtime(),
                expectedAddress = if (isBroadcast) {
                    null
                } else {
                    targetAddress
                },
                expectedPort = targetPort,
                expectedNodeId = expectedNodeId
            )

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

                pendingProbes.remove(sequence)

                if (running) {
                    Log.e(TAG, "Probe send failed", e)
                }
            }
        }
    }

    // ---------------------------------------------------------
    // Probe listening
    // ---------------------------------------------------------

    private fun startListening() {

        val activeSocket = socket ?: return

        scope.launch {

            val buffer = ByteArray(RECEIVE_BUFFER_SIZE)

            while (
                isActive &&
                running &&
                !activeSocket.isClosed
            ) {

                try {

                    val packet =
                        DatagramPacket(buffer, buffer.size)

                    activeSocket.receive(packet)

                    if (!running || socket !== activeSocket) {
                        break
                    }

                    handleMessage(
                        data = packet.data,
                        length = packet.length,
                        address = packet.address,
                        port = packet.port
                    )

                } catch (e: Exception) {

                    if (running && !activeSocket.isClosed) {
                        Log.e(TAG, "Probe receive failed", e)
                    }
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

            Log.w(TAG, "Invalid probe packet", e)

            return
        }

        if (
            message.senderId.isBlank() ||
            message.senderId == localNodeId
        ) {
            return
        }

        synchronized(peerLock) {

            if (!running) return

            when (message.type) {

                ProbeMessage.Type.REQUEST -> {

                    registerPeer(
                        message = message,
                        address = address,
                        port = port,
                        verified = false
                    )

                    sendProbeResponse(
                        request = message,
                        address = address,
                        port = port
                    )
                }

                ProbeMessage.Type.RESPONSE -> {

                    val pending =
                        pendingProbes[message.sequence]
                            ?: return

                    val now =
                        SystemClock.elapsedRealtime()

                    if (
                        now - pending.sentAt >=
                        PENDING_PROBE_TIMEOUT_MS
                    ) {
                        return
                    }

                    if (
                        pending.expectedAddress != null &&
                        pending.expectedAddress != address
                    ) {
                        return
                    }

                    if (pending.expectedPort != port) {
                        return
                    }

                    if (
                        pending.expectedNodeId != null &&
                        pending.expectedNodeId != message.senderId
                    ) {
                        return
                    }

                    if (
                        !pending.responders.add(message.senderId)
                    ) {
                        return
                    }

                    val rtt =
                        (now - pending.sentAt)
                            .coerceAtLeast(0L)

                    recordRtt(
                        neighbourId = message.senderId,
                        rtt = rtt
                    )

                    lastResponseTimes[message.senderId] = now

                    registerPeer(
                        message = message,
                        address = address,
                        port = port,
                        verified = true
                    )

                    notifyCallback {
                        onProbeResponse?.invoke(message.senderId)
                    }
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

        scope.launch {

            try {

                val response = ProbeMessage(
                    type = ProbeMessage.Type.RESPONSE,
                    senderId = localNodeId,
                    sequence = request.sequence,
                    timestamp = request.timestamp,
                    capabilities = getCapabilities()
                )

                val data = ProbeCodec.encode(response)

                activeSocket.send(
                    DatagramPacket(
                        data,
                        data.size,
                        address,
                        port
                    )
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
        message: ProbeMessage,
        address: InetAddress,
        port: Int,
        verified: Boolean
    ) {

        val nodeId = message.senderId

        val ipAddress =
            address.hostAddress ?: return

        val previousPeer =
            peerRegistry.getPeer(nodeId)

        val previousEndpoint =
            probeEndpoints[nodeId]

        val capabilities =
            message.capabilities

        val isNewPeer =
            previousPeer == null

        val wasLost =
            previousPeer?.connectionState ==
                    PeerConnectionState.LOST

        val addressChanged =
            previousEndpoint != null &&
                    (
                            previousEndpoint.address != address ||
                                    previousEndpoint.port != port
                            )

        val now =
            SystemClock.elapsedRealtime()

        if (
            isNewPeer ||
            wasLost ||
            addressChanged
        ) {

            firstDiscoveryTimes[nodeId] = now

            if (!verified) {
                lastResponseTimes.remove(nodeId)
                rttEwma.remove(nodeId)

                synchronized(rttHistory) {
                    rttHistory.remove(nodeId)
                }
            }
        }

        probeEndpoints[nodeId] = ProbeEndpoint(
            address = address,
            port = port
        )

        val connectionState = when {

            verified ->
                PeerConnectionState.CONNECTED

            wasLost || addressChanged ->
                PeerConnectionState.DISCOVERED

            previousPeer?.connectionState ==
                    PeerConnectionState.CONNECTED ->
                PeerConnectionState.CONNECTED

            else ->
                PeerConnectionState.DISCOVERED
        }

        val peer = MeshPeer(
            nodeId = nodeId,

            deviceName =
                capabilities?.deviceName
                    ?: previousPeer?.deviceName
                    ?: "Unknown device",

            ipAddress = ipAddress,

            meshPort =
                capabilities?.meshPort
                    ?: previousPeer?.meshPort
                    ?: 9999,

            connectionState = connectionState,

            lastSeen = System.currentTimeMillis(),

            rssi = null,

            latencyMs = getAverageRtt(nodeId),

            hasInternetAccess =
                capabilities?.hasInternet == true
        )

        peerRegistry.registerPeer(peer)

        if (
            isNewPeer ||
            wasLost ||
            addressChanged
        ) {

            NetworkActivityLog.record(
                message = "Peer discovered",
                detail = "$nodeId @ $ipAddress"
            )

            Log.d(
                TAG,
                "PEER_DISCOVERED: $nodeId @ $ipAddress"
            )
        }

        if (
            addressChanged &&
            !verified &&
            previousPeer?.connectionState ==
            PeerConnectionState.CONNECTED
        ) {

            notifyCallback {
                onPeerLost?.invoke(nodeId)
            }
        }

        notifyCallback {
            onPeerDiscovered?.invoke(
                nodeId,
                ipAddress
            )
        }

        if (
            verified &&
            previousPeer?.connectionState !=
            PeerConnectionState.CONNECTED
        ) {

            Log.d(
                TAG,
                "PEER_CONNECTED: $nodeId | " +
                        "Internet=${peer.hasInternetAccess}"
            )

            notifyCallback {
                onPeerConnected?.invoke(peer)
            }

            if (wasLost || previouslyLost.remove(nodeId)) {

                previouslyLost.remove(nodeId)

                NetworkActivityLog.record(
                    message = "Peer reconnected",
                    detail = nodeId
                )

                Log.d(TAG, "PEER_RECONNECTED: $nodeId")

                notifyCallback {
                    onPeerReconnected?.invoke(peer)
                }
            }
        }

        // Immediately verify newly discovered endpoints.
        if (
            !verified &&
            (isNewPeer || wasLost || addressChanged)
        ) {

            sendProbeInternal(
                targetAddress = address,
                targetPort = port,
                isBroadcast = false,
                expectedNodeId = nodeId
            )
        }
    }

    // ---------------------------------------------------------
    // Peer timeout and reconnection
    // ---------------------------------------------------------

    private val previouslyLost =
        ConcurrentHashMap.newKeySet<String>()

    private fun startTimeoutMonitoring() {

        timeoutJob = scope.launch {

            while (isActive && running) {

                delay(TIMEOUT_CHECK_INTERVAL_MS)

                removeExpiredProbes()
                checkPeerTimeouts()
            }
        }
    }

    private fun checkPeerTimeouts() {

        synchronized(peerLock) {

            if (!running) return

            val now =
                SystemClock.elapsedRealtime()

            peerRegistry.getAllPeers()
                .filter {
                    it.connectionState !=
                            PeerConnectionState.LOST
                }
                .forEach { peer ->

                    val lastResponse =
                        lastResponseTimes[peer.nodeId]
                            ?: firstDiscoveryTimes[peer.nodeId]
                            ?: return@forEach

                    val inactiveDuration =
                        now - lastResponse

                    if (inactiveDuration >= PEER_TIMEOUT_MS) {

                        markPeerLost(peer.nodeId)
                    }
                }
        }
    }

    private fun markPeerLost(nodeId: String) {

        previouslyLost.add(nodeId)

        peerRegistry.markPeerLost(nodeId)

        probeEndpoints.remove(nodeId)
        lastResponseTimes.remove(nodeId)
        firstDiscoveryTimes.remove(nodeId)

        rttEwma.remove(nodeId)

        synchronized(rttHistory) {
            rttHistory.remove(nodeId)
        }

        NetworkActivityLog.record(
            message = "Peer timed out",
            detail = nodeId
        )

        Log.w(TAG, "PEER_LOST: $nodeId")

        notifyCallback {
            onPeerLost?.invoke(nodeId)
        }
    }

    // ---------------------------------------------------------
    // RTT statistics
    // ---------------------------------------------------------

    private fun recordRtt(
        neighbourId: String,
        rtt: Long
    ) {

        synchronized(rttHistory) {

            rttHistory.getOrPut(neighbourId) {
                mutableListOf()
            }.apply {

                add(rtt)

                if (size > MAX_RTT_SAMPLES) {
                    removeAt(0)
                }
            }
        }

        val oldEwma =
            rttEwma[neighbourId] ?: rtt.toDouble()

        val newEwma =
            smoothingAlpha * rtt +
                    (1.0 - smoothingAlpha) * oldEwma

        rttEwma[neighbourId] = newEwma

        Log.d(
            TAG,
            "RTT $neighbourId = ${rtt}ms " +
                    "(EWMA=${newEwma.toLong()}ms)"
        )
    }

    private fun removeExpiredProbes() {

        val cutoff =
            SystemClock.elapsedRealtime() -
                    PENDING_PROBE_TIMEOUT_MS

        pendingProbes.entries.removeIf {
            it.value.sentAt < cutoff
        }
    }

    // ---------------------------------------------------------
    // Local capabilities
    // ---------------------------------------------------------

    private fun getCapabilities(): Capabilities {

        val hasInternet = try {

            internetCapabilityProvider()

        } catch (e: Exception) {

            Log.e(TAG, "Internet capability check failed", e)

            false
        }

        val deviceName =
            "${Build.MANUFACTURER} ${Build.MODEL}".trim()

        return Capabilities(
            hasInternet = hasInternet,
            deviceName = deviceName,
            meshPort = meshPort
        )
    }

    private fun notifyCallback(
        callback: () -> Unit
    ) {

        try {
            callback()
        } catch (e: Exception) {
            Log.e(TAG, "Peer callback failed", e)
        }
    }
}
