package com.orliczspace.mesh_link.ui.state

import com.orliczspace.mesh_link.ui.model.UiGateway
import com.orliczspace.mesh_link.ui.model.UiNode
import com.orliczspace.mesh_link.ui.model.UiPerformance
import com.orliczspace.mesh_link.ui.model.UiRoute

data class DashboardUiState(

    val internetAvailable: Boolean = false,

    val connectionType: String = "Unknown",

    val nodes: List<UiNode> = emptyList(),

    val routes: List<UiRoute> = emptyList(),

    val gateways: List<UiGateway> = emptyList(),

    val performance: UiPerformance = UiPerformance(
        packetsSent = 0,
        packetsReceived = 0,
        packetLoss = 0.0,
        averageLatency = 0.0,
        bandwidth = 0.0,
        activeConnections = 0
    )

)