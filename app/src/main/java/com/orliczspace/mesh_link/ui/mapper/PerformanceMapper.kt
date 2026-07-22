package com.orliczspace.mesh_link.ui.mapper

import com.orliczspace.mesh_link.network.gateway.NatEntry
import com.orliczspace.mesh_link.ui.model.UiPerformance

fun List<NatEntry>.toUiPerformance(): UiPerformance {

    return UiPerformance(

        packetsSent = size.toLong(),

        packetsReceived = size.toLong(),

        packetLoss = 0.0,

        averageLatency = 0.0,

        bandwidth = 0.0,

        activeConnections = size

    )

}