package com.orliczspace.mesh_link.network.crypto

import javax.crypto.SecretKey

data class NeighborSession(
    val neighborNodeId: String,
    val sharedSecret: SecretKey,
    val establishedAt: Long = System.currentTimeMillis()
)
