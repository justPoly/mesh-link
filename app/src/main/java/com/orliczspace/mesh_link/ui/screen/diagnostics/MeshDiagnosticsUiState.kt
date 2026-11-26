package com.orliczspace.mesh_link.ui.screen.diagnostics

import com.orliczspace.mesh_link.network.MeshPeer
import com.orliczspace.mesh_link.network.MeshReachabilityState
import com.orliczspace.mesh_link.network.PeerConnectionState

data class MeshDiagnosticsUiState(

    val localNodeId: String = "",

    val deviceName: String = "",

    val hasDirectInternet: Boolean = false,

    val discoveryPort: Int = 8888,

    val meshPort: Int = 9999,

    val peers: List<PeerDiagnosticUiState> = emptyList(),

    val registeredEndpointCount: Int = 0,

    val routingEntryCount: Int = 0,

    val lastUpdatedAt: Long = System.currentTimeMillis()

) {

    val discoveredPeerCount: Int
        get() = peers.count {
            it.peer.connectionState ==
                    PeerConnectionState.DISCOVERED
        }

    val connectedPeerCount: Int
        get() = peers.count {
            it.peer.connectionState ==
                    PeerConnectionState.CONNECTED
        }

    val reachablePeerCount: Int
        get() = peers.count {
            it.peer.connectionState ==
                    PeerConnectionState.CONNECTED &&
                    it.peer.meshReachability ==
                    MeshReachabilityState.REACHABLE
        }

    val lostPeerCount: Int
        get() = peers.count {
            it.peer.connectionState ==
                    PeerConnectionState.LOST
        }

    val totalTestsSent: Long
        get() = peers.sumOf {
            it.peer.meshTestsSent
        }

    val totalTestsSuccessful: Long
        get() = peers.sumOf {
            it.peer.meshTestsSuccessful
        }

    val totalTestsFailed: Long
        get() = peers.sumOf {
            it.peer.meshTestsFailed
        }

    val diagnosticFailureRate: Double?
        get() {

            val completed =
                totalTestsSuccessful + totalTestsFailed

            if (completed == 0L) return null

            return totalTestsFailed.toDouble() /
                    completed.toDouble() * 100.0
        }
}

data class PeerDiagnosticUiState(

    val peer: MeshPeer,

    val hasRegisteredEndpoint: Boolean = false,

    val hasRoutingEntry: Boolean = false,

    val isGateway: Boolean = false

)
