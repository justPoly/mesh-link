package com.orliczspace.mesh_link.network

/**
 * Represents routing-related state for a single neighbour node.
 * Used by the routing engine to make forwarding decisions.
 */
data class RoutingState(
    val nodeId: String,
    val averageLatencyMs: Long = Long.MAX_VALUE,
    val stabilityScore: Double = 0.0,
    val hasInternetAccess: Boolean = false,
    val gatewayScore: Double = 0.0,
    val isGateway: Boolean = false
)
