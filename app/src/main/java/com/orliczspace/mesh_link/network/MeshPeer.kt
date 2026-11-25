package com.orliczspace.mesh_link.network

enum class PeerConnectionState {
    DISCOVERED,
    CONNECTED,
    LOST
}

data class MeshPeer(
    val nodeId: String,
    val deviceName: String = "Unknown device",
    val ipAddress: String,
    val meshPort: Int = 9999,
    val connectionState: PeerConnectionState =
        PeerConnectionState.DISCOVERED,
    val lastSeen: Long = System.currentTimeMillis(),
    val rssi: Int? = null,
    val latencyMs: Long? = null,
    val hasInternetAccess: Boolean = false
)
