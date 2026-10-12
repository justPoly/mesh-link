
package com.orliczspace.mesh_link.network

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.max

class RoutingStateRepository(
    private val linkProbeService: LinkProbeService
) {

    companion object {
        private const val RTT_DEGRADATION_THRESHOLD = 1.3
        private const val MAX_DEGRADATION_COUNT = 3
    }

    val routingTable = mutableStateMapOf<String, RoutingState>()

    /*
     * Observable gateway error for the dashboard.
     *
     * An error is shown when a previously elected gateway
     * is lost and no replacement is available.
     */
    var gatewayErrorMessage: String? by mutableStateOf(null)
        private set

    /*
     * Observable route error for the dashboard.
     */
    var routeErrorMessage: String? by mutableStateOf(null)
        private set

    /*
     * Tracks whether a route has existed during this
     * repository's lifetime, preventing startup errors.
     */
    private var hadRoutePreviously = false

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

        // Track whether a route has ever been available.
        if (routingTable.isNotEmpty()) {
            hadRoutePreviously = true
        }

        // Clear route errors when a usable Internet gateway exists.
        val hasUsableInternetRoute =
            routingTable.values.any {
                it.isGateway && it.hasInternetAccess
            }

        if (hasUsableInternetRoute) {
            routeErrorMessage = null
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

            /*
             * If the removed node was the gateway,
             * elect a replacement immediately.
             */
            if (removed.isGateway) {
                electGateway(
                    removedGatewayId = removed.nodeId
                )
            }

            // Report route loss if all routing entries disappear.
            if (
                hadRoutePreviously &&
                routingTable.isEmpty()
            ) {
                routeErrorMessage =
                    "The previously available route has been lost. " +
                            "No routes remain in the routing table."
            }
        }
    }

    fun electGateway(
        removedGatewayId: String? = null
    ): RoutingState? {

        val previousGateway =
            routingTable.values
                .firstOrNull { it.isGateway }
                ?.nodeId
                ?: removedGatewayId

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
                    isGateway = it.nodeId == gateway?.nodeId
                )
        }

        val newGateway =
            gateway?.nodeId

        if (previousGateway != newGateway) {

            if (newGateway != null) {
                // A gateway is available again.
                gatewayErrorMessage = null

                NetworkActivityLog.record(
                    message = "Gateway changed",
                    detail = newGateway
                )

            } else if (previousGateway != null) {
                // A previous gateway was lost with no replacement.
                gatewayErrorMessage =
                    "The previous gateway ($previousGateway) is unavailable, " +
                            "and no replacement gateway could be selected."

                NetworkActivityLog.record(
                    message = "Gateway unavailable",
                    detail = previousGateway
                )
            }
        } else if (newGateway != null) {
            gatewayErrorMessage = null
        }

        // Clear route errors when the selected gateway has Internet access.
        if (gateway?.hasInternetAccess == true) {
            routeErrorMessage = null
        }

        return gateway?.let {
            routingTable[it.nodeId]
        }
    }

    fun getRouteToInternet(
        localNodeId: String
    ): RouteDecision? {

        val gateway =
            routingTable.values
                .firstOrNull { it.isGateway }
                ?: return null

        return if (gateway.nodeId == localNodeId) {
            RouteDecision("INTERNET", null, false)
        } else {
            RouteDecision("INTERNET", gateway.nodeId, true)
        }
    }
}
