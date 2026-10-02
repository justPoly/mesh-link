package com.orliczspace.mesh_link.network

import androidx.compose.runtime.mutableStateMapOf
import kotlin.math.max

class RoutingStateRepository(
    private val linkProbeService: LinkProbeService
) {

    companion object {
        private const val RTT_DEGRADATION_THRESHOLD = 1.3
        private const val MAX_DEGRADATION_COUNT = 3
    }

    val routingTable = mutableStateMapOf<String, RoutingState>()

    fun updateNode(
        nodeId: String,
        hasInternetAccess: Boolean
    ) {
        val smoothedRttLong =
            linkProbeService.getAverageRtt(nodeId) ?: return

        val smoothedRtt =
            smoothedRttLong.toInt()

        val stability =
            linkProbeService.getStability(nodeId)

        val oldState =
            routingTable[nodeId]

        val lastRtt =
            oldState?.averageLatencyMs ?: smoothedRtt

        val degradationRatio =
            if (lastRtt > 0) {
                smoothedRtt.toDouble() / lastRtt
            } else {
                1.0
            }

        val degradationCount =
            if (degradationRatio > RTT_DEGRADATION_THRESHOLD) {
                (oldState?.degradationCount ?: 0) + 1
            } else {
                0
            }

        val stabilityScore =
            when {
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

        if (oldState == null) {
            NetworkActivityLog.record(
                message = "Route added",
                detail = nodeId
            )
        }

        if (
            oldState?.isGateway == true &&
            degradationCount >= MAX_DEGRADATION_COUNT
        ) {
            electGateway()
        }
    }

    fun removeNode(nodeId: String) {
        val removed =
            routingTable.remove(nodeId)

        if (removed != null) {
            NetworkActivityLog.record(
                message = "Node removed",
                detail = nodeId
            )
        }
    }

    fun electGateway(): RoutingState? {

        val previousGateway =
            routingTable.values
                .firstOrNull { it.isGateway }
                ?.nodeId

        val updated =
            routingTable.values.map {
                it.copy(
                    gatewayScore = GatewayScorer.score(it),
                    degradationCount = 0
                )
            }

        val gateway =
            updated.maxByOrNull {
                it.gatewayScore
            }

        routingTable.clear()

        updated.forEach {
            routingTable[it.nodeId] =
                it.copy(
                    isGateway =
                        it.nodeId == gateway?.nodeId
                )
        }

        val newGateway =
            gateway?.nodeId

        if (previousGateway != newGateway) {

            if (newGateway != null) {
                NetworkActivityLog.record(
                    message = "Gateway changed",
                    detail = newGateway
                )
            } else if (previousGateway != null) {
                NetworkActivityLog.record(
                    message = "Gateway unavailable",
                    detail = previousGateway
                )
            }
        }

        return gateway
    }

    fun getRouteToInternet(localNodeId: String): RouteDecision? {
        val gateway = routingTable.values.firstOrNull { it.isGateway } ?: return null

        return if (gateway.nodeId == localNodeId) {
            RouteDecision("INTERNET", null, false)
        } else {
            RouteDecision("INTERNET", gateway.nodeId, true)
        }
    }
}
