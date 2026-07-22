package com.orliczspace.mesh_link.ui.mapper

import com.orliczspace.mesh_link.network.RoutingState
import com.orliczspace.mesh_link.ui.model.UiGateway

fun RoutingState.toUiGateway(): UiGateway {

    return UiGateway(

        nodeName = nodeId,

        internetAvailable = hasInternetAccess,

        connectionType = "Wi-Fi",

        latency = averageLatencyMs,

        score = gatewayScore

    )

}