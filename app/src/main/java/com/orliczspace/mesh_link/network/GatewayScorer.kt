package com.orliczspace.mesh_link.network

import kotlin.math.max
import kotlin.math.min

/**
 * Computes gateway score for a node based on RTT, stability, and capabilities.
 * Higher score → more suitable as gateway.
 */
object GatewayScorer {

    /**
     * Compute a normalized score (0.0 - 100.0)
     */
    fun score(state: RoutingState): Double {
        // RTT contribution (lower RTT → higher score)
        // Assume max RTT we care about is 500ms, clamp to 0-500
        val rtt = min(state.averageLatencyMs.toDouble(), 500.0)
        val rttScore = ((500.0 - rtt) / 500.0) * 50.0  // weight 50

        // Stability contribution (higher stability → higher score)
        val stabilityScore = state.stabilityScore * 0.5 // weight 50

        // Internet access bonus
        val internetBonus = if (state.hasInternetAccess) 10.0 else 0.0

        val totalScore = rttScore + stabilityScore + internetBonus
        return totalScore
    }
}
