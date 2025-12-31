package com.orliczspace.mesh_link.network

object GatewayScorer {

    fun score(state: RoutingState): Double {
        val internetBonus = if (state.hasInternetAccess) 1000.0 else 0.0
        val stabilityScore = state.stabilityScore * 2
        val latencyPenalty =
            if (state.averageLatencyMs == Long.MAX_VALUE) 500.0
            else state.averageLatencyMs / 2.0

        return internetBonus + stabilityScore - latencyPenalty
    }
}
