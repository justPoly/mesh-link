package com.orliczspace.mesh_link.ui.screen.diagnostics

import android.os.Build

import com.orliczspace.mesh_link.network.InternetMonitor
import com.orliczspace.mesh_link.network.LinkProbeService
import com.orliczspace.mesh_link.network.MeshReachabilityService
import com.orliczspace.mesh_link.network.PacketForwarder
import com.orliczspace.mesh_link.network.PeerRegistry
import com.orliczspace.mesh_link.network.PeerConnectionState
import com.orliczspace.mesh_link.network.RoutingStateRepository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeshDiagnosticsViewModel(

    private val localNodeId: String,

    private val peerRegistry: PeerRegistry,

    private val packetForwarder: PacketForwarder,

    private val routingRepository: RoutingStateRepository,

    private val internetMonitor: InternetMonitor,

    private val linkProbeService: LinkProbeService,

    private val meshReachabilityService:
    MeshReachabilityService

) {

    private val _uiState =
        MutableStateFlow(
            MeshDiagnosticsUiState(
                localNodeId = localNodeId,
                deviceName =
                    "${Build.MANUFACTURER} ${Build.MODEL}"
            )
        )

    val uiState = _uiState.asStateFlow()

    // ---------------------------------------------------------
    // Refresh diagnostic information
    // Call from the main thread.
    // ---------------------------------------------------------

    fun refresh() {

        val registeredEndpoints =
            packetForwarder.getRegisteredPeerAddresses()

        val routingEntries =
            routingRepository.routingTable.toMap()

        val peers =
            peerRegistry.getAllPeers()

                .sortedWith(

                    compareBy {
                            peer ->

                        when (peer.connectionState) {

                            PeerConnectionState.CONNECTED -> 0

                            PeerConnectionState.DISCOVERED -> 1

                            PeerConnectionState.LOST -> 2
                        }
                    }
                )

                .map { peer ->

                    PeerDiagnosticUiState(

                        peer = peer,

                        hasRegisteredEndpoint =
                            registeredEndpoints
                                .containsKey(peer.nodeId),

                        hasRoutingEntry =
                            routingEntries
                                .containsKey(peer.nodeId),

                        isGateway =
                            routingEntries[
                                peer.nodeId
                            ]?.isGateway == true
                    )
                }

        _uiState.value =
            MeshDiagnosticsUiState(

                localNodeId = localNodeId,

                deviceName =
                    "${Build.MANUFACTURER} ${Build.MODEL}",

                hasDirectInternet =
                    internetMonitor.hasDirectInternetAccess(),

                discoveryPort = 8888,

                meshPort = 9999,

                peers = peers,

                registeredEndpointCount =
                    registeredEndpoints.size,

                routingEntryCount =
                    routingEntries.size,

                lastUpdatedAt =
                    System.currentTimeMillis()
            )
    }

    // ---------------------------------------------------------
    // Request discovery
    // ---------------------------------------------------------

    fun requestDiscovery() {

        linkProbeService.requestDiscovery()

        refresh()
    }

    // ---------------------------------------------------------
    // Test individual peer
    // ---------------------------------------------------------

    fun testPeer(nodeId: String): Boolean {

        val started =
            meshReachabilityService.testPeer(nodeId)

        refresh()

        return started
    }

    // ---------------------------------------------------------
    // Test all connected peers
    // ---------------------------------------------------------

    fun testAllPeers() {

        meshReachabilityService.testAllConnectedPeers()

        refresh()
    }

    // ---------------------------------------------------------
    // Generate diagnostic report
    // ---------------------------------------------------------

    fun generateReport(): String {

        refresh()

        val state = _uiState.value

        val dateFormat =
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss Z",
                Locale.getDefault()
            )

        return buildString {

            appendLine("MESH-LINK DIAGNOSTIC REPORT")

            appendLine("================================")

            appendLine(
                "Generated: ${
                    dateFormat.format(
                        Date(state.lastUpdatedAt)
                    )
                }"
            )

            appendLine()

            appendLine("LOCAL NODE")

            appendLine("Device: ${state.deviceName}")

            appendLine("Node ID: ${state.localNodeId}")

            appendLine(
                "Direct internet: ${state.hasDirectInternet}"
            )

            appendLine(
                "Discovery port: ${state.discoveryPort}"
            )

            appendLine(
                "Mesh port: ${state.meshPort}"
            )

            appendLine()

            appendLine("NETWORK SUMMARY")

            appendLine(
                "Known peers: ${state.peers.size}"
            )

            appendLine(
                "Discovered: ${state.discoveredPeerCount}"
            )

            appendLine(
                "Connected: ${state.connectedPeerCount}"
            )

            appendLine(
                "Reachable: ${state.reachablePeerCount}"
            )

            appendLine(
                "Lost: ${state.lostPeerCount}"
            )

            appendLine(
                "Registered endpoints: " +
                        state.registeredEndpointCount
            )

            appendLine(
                "Routing entries: ${state.routingEntryCount}"
            )

            appendLine()

            appendLine("MESH TRANSPORT STATISTICS")

            appendLine(
                "Tests sent: ${state.totalTestsSent}"
            )

            appendLine(
                "Tests passed: ${state.totalTestsSuccessful}"
            )

            appendLine(
                "Tests failed: ${state.totalTestsFailed}"
            )

            appendLine(
                "Diagnostic failure rate: ${
                    formatPercent(
                        state.diagnosticFailureRate
                    )
                }"
            )

            state.peers.forEachIndexed { index, item ->

                val peer = item.peer

                appendLine()

                appendLine(
                    "PEER ${index + 1}"
                )

                appendLine("--------------------------------")

                appendLine("Device: ${peer.deviceName}")

                appendLine("Node ID: ${peer.nodeId}")

                appendLine("IP address: ${peer.ipAddress}")

                appendLine("Mesh port: ${peer.meshPort}")

                appendLine(
                    "Connection state: ${peer.connectionState}"
                )

                appendLine(
                    "Last seen: ${
                        dateFormat.format(Date(peer.lastSeen))
                    }"
                )

                appendLine(
                    "Discovery latency: ${
                        peer.latencyMs?.let { "$it ms" }
                            ?: "Unavailable"
                    }"
                )

                appendLine(
                    "Mesh latency: ${
                        peer.meshLatencyMs?.let { "$it ms" }
                            ?: "Unavailable"
                    }"
                )

                appendLine(
                    "RSSI: ${
                        peer.rssi?.let { "$it dBm" }
                            ?: "Unavailable"
                    }"
                )

                appendLine(
                    "Internet capability: ${peer.hasInternetAccess}"
                )

                appendLine(
                    "Mesh reachability: ${peer.meshReachability}"
                )

                appendLine(
                    "Registered endpoint: " +
                            item.hasRegisteredEndpoint
                )

                appendLine(
                    "Routing entry: ${item.hasRoutingEntry}"
                )

                appendLine(
                    "Gateway candidate selected: ${item.isGateway}"
                )

                appendLine(
                    "Last mesh test: ${
                        peer.lastMeshTestAt?.let {
                            dateFormat.format(Date(it))
                        } ?: "Not tested"
                    }"
                )

                appendLine(
                    "Tests sent: ${peer.meshTestsSent}"
                )

                appendLine(
                    "Tests passed: ${peer.meshTestsSuccessful}"
                )

                appendLine(
                    "Tests failed: ${peer.meshTestsFailed}"
                )

                appendLine(
                    "Diagnostic failure rate: ${
                        formatPercent(
                            peer.meshPacketLossPercent
                        )
                    }"
                )
            }

            appendLine()

            appendLine(
                "Note: Diagnostic failure rate represents " +
                        "failed round-trip tests, not general " +
                        "network packet loss."
            )

            appendLine(
                "Gateway selection does not authorize " +
                        "remote internet forwarding."
            )
        }
    }

    private fun formatPercent(value: Double?): String {

        return value?.let {

            String.format(
                Locale.US,
                "%.1f%%",
                it
            )

        } ?: "Not available"
    }
}
