package com.orliczspace.mesh_link.network

import android.os.SystemClock
import android.util.Log

import com.orliczspace.mesh_link.network.packet.MeshPacket
import com.orliczspace.mesh_link.network.packet.PacketType

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import java.util.concurrent.ConcurrentHashMap

class MeshReachabilityService(
    private val localNodeId: String,
    private val peerRegistry: PeerRegistry,
    private val packetForwarder: PacketForwarder
) {

    companion object {

        private const val TAG =
            "MeshReachability"

        private const val TEST_TIMEOUT_MS =
            5_000L

        private const val TEST_TTL = 1
    }

    private val scope =
        CoroutineScope(
            Dispatchers.IO + SupervisorJob()
        )

    private val testLock = Any()

    @Volatile
    private var stopped = false

    private data class PendingTest(

        val packetId: String,

        val nodeId: String,

        val sentAt: Long,

        val ipAddress: String,

        val meshPort: Int
    )

    private val pendingTests =
        ConcurrentHashMap<String, PendingTest>()

    // ---------------------------------------------------------
    // Start a mesh reachability test
    // ---------------------------------------------------------

    fun testPeer(nodeId: String): Boolean {

        synchronized(testLock) {

            if (
                stopped ||
                nodeId.isBlank() ||
                nodeId == localNodeId
            ) {
                return false
            }

            val peer =
                peerRegistry.getPeer(nodeId)
                    ?: return false

            if (
                peer.connectionState !=
                PeerConnectionState.CONNECTED
            ) {

                Log.w(
                    TAG,
                    "Cannot test disconnected peer: $nodeId"
                )

                return false
            }

            if (
                !packetForwarder.hasPeerAddress(nodeId)
            ) {

                Log.w(
                    TAG,
                    "No registered mesh endpoint: $nodeId"
                )

                return false
            }

            if (
                pendingTests.values.any {
                    it.nodeId == nodeId
                }
            ) {

                Log.d(
                    TAG,
                    "Test already running for $nodeId"
                )

                return false
            }

            val packet =
                MeshPacket(

                    sourceNodeId =
                        localNodeId,

                    destinationNodeId =
                        nodeId,

                    payload =
                        ByteArray(0),

                    ttl =
                        TEST_TTL,

                    type =
                        PacketType.RTT_PROBE,

                    encrypted =
                        false,

                    requiresAck =
                        false
                )

            val pending =
                PendingTest(

                    packetId =
                        packet.packetId,

                    nodeId =
                        nodeId,

                    sentAt =
                        SystemClock.elapsedRealtime(),

                    ipAddress =
                        peer.ipAddress,

                    meshPort =
                        peer.meshPort
                )

            pendingTests[packet.packetId] =
                pending

            peerRegistry.markMeshTestStarted(
                nodeId
            )

            Log.d(
                TAG,
                "MESH_TEST_STARTED: " +
                        "node=$nodeId | " +
                        "packet=${packet.packetId}"
            )

            // Send the diagnostic packet.

            scope.launch {

                packetForwarder.forward(packet)
            }

            // Independently monitor the response timeout.

            scope.launch {

                delay(TEST_TIMEOUT_MS)

                synchronized(testLock) {

                    val expired =
                        pendingTests.remove(
                            packet.packetId
                        )

                    if (expired != null) {

                        peerRegistry.markMeshTestFailed(
                            expired.nodeId
                        )

                        Log.w(
                            TAG,
                            "MESH_TEST_TIMEOUT: " +
                                    "node=${expired.nodeId} | " +
                                    "packet=${expired.packetId}"
                        )
                    }
                }
            }

            return true
        }
    }

    // ---------------------------------------------------------
    // Test every connected peer
    // ---------------------------------------------------------

    fun testAllConnectedPeers() {

        peerRegistry.getConnectedPeers()
            .forEach { peer ->

                testPeer(peer.nodeId)
            }
    }

    // ---------------------------------------------------------
    // Handle incoming diagnostic packets
    // ---------------------------------------------------------

    fun handlePacket(packet: MeshPacket): Boolean {

        if (
            packet.type != PacketType.RTT_PROBE &&
            packet.type != PacketType.RTT_RESPONSE
        ) {
            return false
        }

        if (
            stopped ||
            packet.destinationNodeId != localNodeId ||
            packet.sourceNodeId == localNodeId ||
            packet.sourceNodeId.isBlank() ||
            packet.encrypted ||
            packet.ttl != TEST_TTL ||
            packet.payload.isNotEmpty()
        ) {
            return true
        }

        val peer =
            peerRegistry.getPeer(
                packet.sourceNodeId
            )

        if (
            peer?.connectionState !=
            PeerConnectionState.CONNECTED
        ) {

            Log.w(
                TAG,
                "Rejected diagnostic packet from " +
                        "unverified peer"
            )

            return true
        }

        when (packet.type) {

            PacketType.RTT_PROBE -> {

                handleProbe(packet)
            }

            PacketType.RTT_RESPONSE -> {

                handleResponse(packet)
            }

            else -> Unit
        }

        return true
    }

    // ---------------------------------------------------------
    // Respond to an incoming mesh probe
    // ---------------------------------------------------------

    private fun handleProbe(packet: MeshPacket) {

        val response =
            MeshPacket(

                sourceNodeId =
                    localNodeId,

                destinationNodeId =
                    packet.sourceNodeId,

                payload =
                    ByteArray(0),

                ttl =
                    TEST_TTL,

                type =
                    PacketType.RTT_RESPONSE,

                ackForPacketId =
                    packet.packetId,

                encrypted =
                    false,

                requiresAck =
                    false
            )

        Log.d(
            TAG,
            "MESH_PROBE_RECEIVED: " +
                    "node=${packet.sourceNodeId} | " +
                    "packet=${packet.packetId}"
        )

        scope.launch {

            packetForwarder.forward(response)
        }
    }

    // ---------------------------------------------------------
    // Process matching response
    // ---------------------------------------------------------

    private fun handleResponse(packet: MeshPacket) {

        val originalPacketId =
            packet.ackForPacketId ?: return

        synchronized(testLock) {

            val pending =
                pendingTests[originalPacketId]
                    ?: return

            if (
                pending.nodeId != packet.sourceNodeId
            ) {

                Log.w(
                    TAG,
                    "Rejected response from unexpected node"
                )

                return
            }

            val peer =
                peerRegistry.getPeer(
                    pending.nodeId
                )

            if (
                peer == null ||
                peer.connectionState !=
                PeerConnectionState.CONNECTED ||
                peer.ipAddress != pending.ipAddress ||
                peer.meshPort != pending.meshPort
            ) {

                pendingTests.remove(
                    originalPacketId
                )

                peerRegistry.markMeshTestFailed(
                    pending.nodeId
                )

                return
            }

            val latency =
                SystemClock.elapsedRealtime() -
                        pending.sentAt

            pendingTests.remove(
                originalPacketId
            )

            if (latency >= TEST_TIMEOUT_MS) {

                peerRegistry.markMeshTestFailed(
                    pending.nodeId
                )

                return
            }

            peerRegistry.markMeshTestSuccessful(
                nodeId = pending.nodeId,
                latencyMs = latency
            )

            Log.d(
                TAG,
                "MESH_TEST_PASSED: " +
                        "node=${pending.nodeId} | " +
                        "RTT=${latency}ms"
            )
        }
    }

    // ---------------------------------------------------------
    // Cancel tests for a disconnected peer
    // ---------------------------------------------------------

    fun cancelPeerTests(nodeId: String) {

        synchronized(testLock) {

            val tests =
                pendingTests.values.filter {

                    it.nodeId == nodeId
                }

            tests.forEach { test ->

                if (
                    pendingTests.remove(
                        test.packetId,
                        test
                    )
                ) {

                    peerRegistry.markMeshTestFailed(
                        nodeId
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------
    // Stop diagnostics
    // ---------------------------------------------------------

    fun stop() {

        synchronized(testLock) {

            if (stopped) return

            stopped = true

            pendingTests.clear()

            scope.cancel()
        }
    }
}
