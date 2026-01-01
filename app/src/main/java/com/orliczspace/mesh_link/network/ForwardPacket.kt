package com.orliczspace.mesh_link.network

/**
 * Simple forwarding envelope.
 * Payload is opaque (can be anything later).
 */
data class ForwardPacket(
    val sourceNodeId: String,
    val destinationNodeId: String,
    val ttl: Int,
    val payload: ByteArray
)
