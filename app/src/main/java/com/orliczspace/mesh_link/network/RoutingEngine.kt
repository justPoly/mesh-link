package com.orliczspace.mesh_link.network

import android.util.Log

/**
 * Selects the best neighbour for mesh-assisted internet routing
 * based on probe-derived link quality metrics.
 */
class RoutingEngine(
    private val linkProbeService: LinkProbeService,
    private val internetMonitor: InternetMonitor
) {

    /**
     * Chooses the optimal neighbour node to relay traffic.
     */
    fun selectBestRelay(neighbours: List<String>): String? {

        // If device has strong internet, do NOT relay
        if (internetMonitor.isConnected.value) {
            Log.d("RoutingEngine", "Using direct internet connection")
            return null
        }

        var bestNode: String? = null
        var bestScore = Double.MIN_VALUE

        neighbours.forEach { nodeId ->

            val avgRtt = linkProbeService.getAverageRtt(nodeId) ?: return@forEach
            val stability = linkProbeService.getStability(nodeId)

            val lossPenalty = 0.1 // placeholder until loss metric added

            val score = computeLqs(
                avgRtt = avgRtt,
                stability = stability,
                loss = lossPenalty
            )

            Log.d("RoutingEngine", "Node $nodeId → LQS=$score")

            if (score > bestScore) {
                bestScore = score
                bestNode = nodeId
            }
        }

        Log.d("RoutingEngine", "Selected relay: $bestNode")
        return bestNode
    }

    private fun computeLqs(
        avgRtt: Long,
        stability: Double,
        loss: Double
    ): Double {
        val w1 = 0.5
        val w2 = 0.3
        val w3 = 0.2

        return (w1 * (1.0 / avgRtt)) +
                (w2 * (1 - loss)) +
                (w3 * stability)
    }
}
