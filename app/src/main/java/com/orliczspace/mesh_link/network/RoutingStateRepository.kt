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

    var gatewayErrorMessage: String? by mutableStateOf(null)
        private set

    var routeErrorMessage: String? by mutableStateOf(null)
        private set

    private var hadRoutePreviously = false

    fun updateNode(
        nodeId: String,
        hasInternetAccess: Boolean
    ) {
        val smoothedRttLong =
            linkProbeService.getAverageRtt(nodeId) ?: return

        val smoothedRtt =
            max(smoothedRttLong.toInt(), 0)

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

        val newState = RoutingState(
            nodeId = nodeId,
            averageLatencyMs = smoothedRtt,
            lastLatencyMs = lastRtt,
            degradationCount = degradationCount,
            stabilityScore = stabilityScore,
            hasInternetAccess = hasInternetAccess,
            isGateway = oldState?.isGateway ?: false
        )

        routingTable[nodeId] = newState.copy(
            gatewayScore = GatewayScorer.score(newState)
        )

        if (oldState == null) {
            NetworkActivityLog.record(
                message = "Route added",
                detail = nodeId
            )
        }

        if (
            oldState != null &&
            oldState.hasInternetAccess != hasInternetAccess
        ) {
            NetworkActivityLog.record(
                message = "Internet capability changed",
                detail = "$nodeId: $hasInternetAccess"
            )
        }

        val currentGateway =
            routingTable.values.firstOrNull {
                it.isGateway
            }

        val shouldElectGateway =
            currentGateway == null ||
                    !currentGateway.hasInternetAccess ||
                    oldState == null ||
                    oldState.hasInternetAccess != hasInternetAccess ||
                    (
                            currentGateway.nodeId == nodeId &&
                                    degradationCount >= MAX_DEGRADATION_COUNT
                            )

        if (shouldElectGateway) {
            electGateway()
        }

        updateRouteStatus()
    }

    fun removeNode(nodeId: String) {
        val removed =
            routingTable.remove(nodeId) ?: return

        NetworkActivityLog.record(
            message = "Node removed",
            detail = nodeId
        )

        if (removed.isGateway) {
            electGateway(
                removedGatewayId = removed.nodeId
            )
        }

        updateRouteStatus()
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
                    gatewayScore = GatewayScorer.score(it)
                )
            }

        // Only nodes advertising Internet access
        // are eligible to become a gateway.
        val eligibleGateways =
            updated.filter {
                it.hasInternetAccess
            }

        val gateway =
            eligibleGateways
                .sortedWith(
                    compareByDescending<RoutingState> {
                        it.gatewayScore
                    }.thenBy {
                        it.nodeId
                    }
                )
                .firstOrNull()

        updated.forEach { state ->
            routingTable[state.nodeId] =
                state.copy(
                    isGateway =
                        state.nodeId == gateway?.nodeId,
                    degradationCount = 0
                )
        }

        val newGateway = gateway?.nodeId

        if (newGateway != null) {

            gatewayErrorMessage = null

            if (previousGateway != newGateway) {
                NetworkActivityLog.record(
                    message = "Gateway changed",
                    detail = newGateway
                )
            }

        } else if (previousGateway != null) {

            gatewayErrorMessage =
                "The previous gateway ($previousGateway) " +
                        "is unavailable, and no replacement " +
                        "gateway could be selected."

            NetworkActivityLog.record(
                message = "Gateway unavailable",
                detail = previousGateway
            )
        }

        updateRouteStatus()

        return newGateway?.let {
            routingTable[it]
        }
    }

    private fun updateRouteStatus() {

        val hasUsableInternetRoute =
            routingTable.values.any {
                it.isGateway && it.hasInternetAccess
            }

        if (hasUsableInternetRoute) {

            hadRoutePreviously = true
            routeErrorMessage = null

        } else if (hadRoutePreviously) {

            routeErrorMessage =
                "The previously available Internet " +
                        "route has been lost."
        }
    }

    fun getRouteToInternet(
        localNodeId: String
    ): RouteDecision? {

        val gateway =
            routingTable.values.firstOrNull {
                it.isGateway && it.hasInternetAccess
            } ?: return null

        return if (gateway.nodeId == localNodeId) {

            RouteDecision(
                "INTERNET",
                null,
                false
            )

        } else {

            RouteDecision(
                "INTERNET",
                gateway.nodeId,
                true
            )
        }
    }
}
