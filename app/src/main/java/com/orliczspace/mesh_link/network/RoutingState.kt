package com.orliczspace.mesh_link.network

/**
 * Represents routing-related state for a single neighbour node.
 * Used by the routing engine to make forwarding decisions.
 */
data class RoutingState(
    val nodeId: String,
    val lastSeenTimestamp: Long,

    // Probe-based metrics
    val averageLatencyMs: Long,
    val packetLossRate: Double,

    // Stability score derived from probe success rate
    val stabilityScore: Double,

    // Whether this node currently has internet access
    val hasInternetAccess: Boolean
)
