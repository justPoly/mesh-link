package com.orliczspace.mesh_link.network

import androidx.compose.runtime.mutableStateMapOf
import kotlin.math.max

class RoutingStateRepository(
    private val linkProbeService: LinkProbeService
) {

    companion object {
        private const val RTT_DEGRADATION_THRESHOLD = 1.3
        private const val MAX_DEGRADATION_COUNT = 3

        // Smoothing factor α for EWMA: higher α → more sensitive to recent RTT
        private const val RTT_SMOOTHING_ALPHA = 0.3
    }

    val routingTable = mutableStateMapOf<String, RoutingState>()

    fun updateNode(
        nodeId: String,
        hasInternetAccess: Boolean
    ) {
        val newRttLong = linkProbeService.getAverageRtt(nodeId) ?: return
        val newRtt = newRttLong.toInt()
        val stability = linkProbeService.getStability(nodeId)
        val oldState = routingTable[nodeId]

        // ----------- EWMA RTT smoothing -----------
        val smoothedRtt = if (oldState != null) {
            // RTT_EWMA_new = α * RTT_new + (1 - α) * RTT_old
            (RTT_SMOOTHING_ALPHA * newRtt + (1 - RTT_SMOOTHING_ALPHA) * oldState.averageLatencyMs).toInt()
        } else {
            newRtt
        }

        val lastRtt = oldState?.averageLatencyMs ?: smoothedRtt

        val degradationRatio =
            if (lastRtt > 0)
                smoothedRtt.toDouble() / lastRtt.toDouble()
            else
                1.0

        val degradationCount =
            if (degradationRatio > RTT_DEGRADATION_THRESHOLD)
                (oldState?.degradationCount ?: 0) + 1
            else
                0

        val stabilityScore = when {
            stability <= 10 -> 100.0
            stability <= 30 -> 80.0
            stability <= 60 -> 60.0
            stability <= 100 -> 40.0
            else -> 20.0
        }

        routingTable[nodeId] = RoutingState(
            nodeId = nodeId,
            averageLatencyMs = max(smoothedRtt, 0),
            lastLatencyMs = lastRtt,
            degradationCount = degradationCount,
            stabilityScore = stabilityScore,
            hasInternetAccess = hasInternetAccess,
            isGateway = oldState?.isGateway ?: false
        )

        // 🔥 Adaptive re-election trigger
        if (
            oldState?.isGateway == true &&
            degradationCount >= MAX_DEGRADATION_COUNT
        ) {
            electGateway()
        }
    }

    fun removeNode(nodeId: String) {
        routingTable.remove(nodeId)
    }

    fun electGateway(): RoutingState? {
        val updatedStates = routingTable.values.map { state ->
            val score = GatewayScorer.score(state)
            state.copy(
                gatewayScore = score,
                degradationCount = 0 // reset after election
            )
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
            RouteDecision(
                destination = "INTERNET",
                nextHopNodeId = null,
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
