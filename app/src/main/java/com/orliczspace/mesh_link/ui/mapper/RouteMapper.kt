package com.orliczspace.mesh_link.ui.mapper

import com.orliczspace.mesh_link.network.RoutingState
import com.orliczspace.mesh_link.ui.model.UiRoute

fun RoutingState.toUiRoute(): UiRoute {

    return UiRoute(

        destination = ip,

        nextHop = nodeId,

        hops = 1,

        latency = averageLatencyMs,

        stability = stabilityScore,

        active = hasInternetAccess

    )
}