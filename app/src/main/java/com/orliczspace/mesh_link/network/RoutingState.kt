package com.orliczspace.mesh_link.network

/**
 * Represents routing-related state for a single neighbour node.
 * Used by the routing engine to make forwarding decisions.
 */
data class RoutingState(
    val nodeId: String,
    val averageLatencyMs: Int,
    val stabilityScore: Double,
    val hasInternetAccess: Boolean,
    val gatewayScore: Double = 0.0,
    val isGateway: Boolean = false,
    val lastLatencyMs: Int = averageLatencyMs,
    val degradationCount: Int = 0,
    val ip: String = "127.0.0.1",
    val port: Int = 12345
)

