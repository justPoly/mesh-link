package com.orliczspace.mesh_link.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PeerRegistry {

    private val _peers =
        MutableStateFlow<Map<String, MeshPeer>>(
            emptyMap()
        )

    val peers = _peers.asStateFlow()

    // ---------------------------------------------------------
    // Register or update discovered peer
    // ---------------------------------------------------------

    fun registerPeer(peer: MeshPeer) {

        _peers.update { current ->

            val existing =
                current[peer.nodeId]

            val updatedPeer =
                if (existing == null) {

                    peer

                } else {

                    val endpointChanged =
                        existing.ipAddress != peer.ipAddress ||
                                existing.meshPort != peer.meshPort

                    val connectionLost =
                        peer.connectionState ==
                                PeerConnectionState.LOST

                    val wasLost =
                        existing.connectionState ==
                                PeerConnectionState.LOST

                    val resetReachability =
                        endpointChanged ||
                                connectionLost ||
                                wasLost

                    peer.copy(

                        meshReachability =
                            if (resetReachability) {

                                MeshReachabilityState.NOT_TESTED

                            } else {

                                existing.meshReachability
                            },

                        meshLatencyMs =
                            if (resetReachability) {
                                null
                            } else {
                                existing.meshLatencyMs
                            },

                        lastMeshTestAt =
                            existing.lastMeshTestAt,

                        meshTestsSent =
                            existing.meshTestsSent,

                        meshTestsSuccessful =
                            existing.meshTestsSuccessful,

                        meshTestsFailed =
                            existing.meshTestsFailed
                    )
                }

            current + (
                    peer.nodeId to updatedPeer
                    )
        }
    }

    // ---------------------------------------------------------
    // Retrieve peers
    // ---------------------------------------------------------

    fun getPeer(nodeId: String): MeshPeer? {

        return _peers.value[nodeId]
    }

    fun getAllPeers(): List<MeshPeer> {

        return _peers.value.values.toList()
    }

    fun getConnectedPeers(): List<MeshPeer> {

        return _peers.value.values.filter {

            it.connectionState ==
                    PeerConnectionState.CONNECTED
        }
    }

    fun getInternetCapablePeers(): List<MeshPeer> {

        return _peers.value.values.filter {

            it.connectionState ==
                    PeerConnectionState.CONNECTED &&
                    it.hasInternetAccess
        }
    }

    fun getMeshReachablePeers(): List<MeshPeer> {

        return _peers.value.values.filter {

            it.connectionState ==
                    PeerConnectionState.CONNECTED &&

                    it.meshReachability ==
                    MeshReachabilityState.REACHABLE
        }
    }

    // ---------------------------------------------------------
    // Update peer information
    // ---------------------------------------------------------

    fun updatePeer(
        nodeId: String,
        transform: (MeshPeer) -> MeshPeer
    ) {

        _peers.update { current ->

            val existing =
                current[nodeId]

            if (existing == null) {

                current

            } else {

                current + (
                        nodeId to transform(existing)
                        )
            }
        }
    }

    // ---------------------------------------------------------
    // Mesh reachability testing
    // ---------------------------------------------------------

    fun markMeshTestStarted(nodeId: String) {

        updatePeer(nodeId) { peer ->

            peer.copy(

                meshReachability =
                    MeshReachabilityState.TESTING,

                meshTestsSent =
                    peer.meshTestsSent + 1
            )
        }
    }

    fun markMeshTestSuccessful(
        nodeId: String,
        latencyMs: Long
    ) {

        updatePeer(nodeId) { peer ->

            if (
                peer.connectionState !=
                PeerConnectionState.CONNECTED
            ) {

                peer

            } else {

                peer.copy(

                    meshReachability =
                        MeshReachabilityState.REACHABLE,

                    meshLatencyMs =
                        latencyMs.coerceAtLeast(0),

                    lastMeshTestAt =
                        System.currentTimeMillis(),

                    meshTestsSuccessful =
                        peer.meshTestsSuccessful + 1
                )
            }
        }
    }

    fun markMeshTestFailed(nodeId: String) {

        updatePeer(nodeId) { peer ->

            peer.copy(

                meshReachability =
                    if (
                        peer.connectionState ==
                        PeerConnectionState.CONNECTED
                    ) {

                        MeshReachabilityState.UNREACHABLE

                    } else {

                        MeshReachabilityState.NOT_TESTED
                    },

                meshLatencyMs = null,

                lastMeshTestAt =
                    System.currentTimeMillis(),

                meshTestsFailed =
                    peer.meshTestsFailed + 1
            )
        }
    }

    // ---------------------------------------------------------
    // Peer lifecycle
    // ---------------------------------------------------------

    fun markPeerLost(nodeId: String) {

        updatePeer(nodeId) { peer ->

            peer.copy(

                connectionState =
                    PeerConnectionState.LOST,

                latencyMs = null,

                rssi = null,

                hasInternetAccess = false,

                meshReachability =
                    MeshReachabilityState.NOT_TESTED,

                meshLatencyMs = null
            )
        }
    }

    fun removePeer(nodeId: String) {

        _peers.update { current ->

            current - nodeId
        }
    }

    fun clear() {

        _peers.value = emptyMap()
    }
}
