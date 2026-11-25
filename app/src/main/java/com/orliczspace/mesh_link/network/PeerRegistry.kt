package com.orliczspace.mesh_link.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PeerRegistry {

    private val _peers =
        MutableStateFlow<Map<String, MeshPeer>>(emptyMap())

    val peers = _peers.asStateFlow()

    fun registerPeer(peer: MeshPeer) {
        _peers.update { current ->
            current + (peer.nodeId to peer)
        }
    }

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

    fun updatePeer(
        nodeId: String,
        transform: (MeshPeer) -> MeshPeer
    ) {
        _peers.update { current ->

            val existing = current[nodeId]

            if (existing == null) {
                current
            } else {
                current + (
                        nodeId to transform(existing)
                        )
            }
        }
    }

    fun markPeerLost(nodeId: String) {
        updatePeer(nodeId) {
            it.copy(
                connectionState =
                    PeerConnectionState.LOST,
                latencyMs = null,
                hasInternetAccess = false
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
