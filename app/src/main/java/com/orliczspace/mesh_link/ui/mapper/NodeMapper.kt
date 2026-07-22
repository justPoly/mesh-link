package com.orliczspace.mesh_link.ui.mapper

import com.orliczspace.mesh_link.network.RoutingState
import com.orliczspace.mesh_link.ui.model.UiNode

fun RoutingState.toUiNode(): UiNode {

    return UiNode(

        id = nodeId,

        name = nodeId,

        ipAddress = ip,

        signalStrength = 100,

        connected = true,

        gateway = isGateway,

        latency = averageLatencyMs

    )

}