package com.orliczspace.mesh_link.network

import androidx.compose.runtime.mutableStateMapOf
import kotlin.math.max

class RoutingStateRepository(
    private val linkProbeService: LinkProbeService
) {

    // Observable routing table (nodeId → RoutingState)
    val routingTable = mutableStateMapOf<String, RoutingState>()

    /**
     * Update routing state for a neighbour.
     * Safe to call even before probes complete.
     */
    fun updateNode(
        nodeId: String,
        hasInternetAccess: Boolean
    ) {
        val avgRtt = linkProbeService.getAverageRtt(nodeId) ?: -1
        val stability = linkProbeService.getStability(nodeId)

        // Packet loss heuristic (temporary model)
        val packetLossRate = when {
            stability <= 20 -> 0.05
            stability <= 50 -> 0.15
            else -> 0.3
        }

        // Normalized stability score (0–100)
        val stabilityScore = when {
            stability <= 10 -> 100.0
            stability <= 30 -> 80.0
            stability <= 60 -> 60.0
            stability <= 100 -> 40.0
            else -> 20.0
        }

        routingTable[nodeId] = RoutingState(
            nodeId = nodeId,
            lastSeenTimestamp = System.currentTimeMillis(),
            averageLatencyMs = max(avgRtt, 0),
            packetLossRate = packetLossRate,
            stabilityScore = stabilityScore,
            hasInternetAccess = hasInternetAccess
        )
    }

    fun removeNode(nodeId: String) {
        routingTable.remove(nodeId)
    }
}
