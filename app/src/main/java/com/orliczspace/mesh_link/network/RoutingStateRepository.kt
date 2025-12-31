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
            averageLatencyMs = max(avgRtt, 0),
            stabilityScore = stabilityScore,
            hasInternetAccess = hasInternetAccess
        )
    }

    fun removeNode(nodeId: String) {
        routingTable.remove(nodeId)
    }

    /**
     * Elect the best gateway based on computed gateway score.
     */
    fun electGateway(): RoutingState? {
        val updatedStates = routingTable.values.map { state ->
            val score = GatewayScorer.score(state)
            state.copy(gatewayScore = score)
        }

        val gateway = updatedStates.maxByOrNull { it.gatewayScore }

        routingTable.clear()
        updatedStates.forEach { state ->
            routingTable[state.nodeId] =
                state.copy(isGateway = state.nodeId == gateway?.nodeId)
        }

        return gateway
    }

    fun getRouteToInternet(localNodeId: String): RouteDecision? {
        val gateway = routingTable.values.firstOrNull { it.isGateway }
            ?: return null

        return if (gateway.nodeId == localNodeId) {
            // We ARE the gateway
            RouteDecision(
                destination = "INTERNET",
                nextHopNodeId = localNodeId,
                viaGateway = false
            )
        } else {
            RouteDecision(
                destination = "INTERNET",
                nextHopNodeId = gateway.nodeId,
                viaGateway = true
            )
        }
    }
}
