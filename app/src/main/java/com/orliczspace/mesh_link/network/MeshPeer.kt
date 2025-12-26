package com.orliczspace.mesh_link.network

/**
 * Represents a connected Wi-Fi Direct peer.
 */
data class MeshPeer(
    val deviceName: String,
    val deviceAddress: String,
    val ipAddress: String?,
    val isGroupOwner: Boolean
)
