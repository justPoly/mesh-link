package com.orliczspace.mesh_link.ui.model

data class UiPerformance(

    val packetsSent: Long,

    val packetsReceived: Long,

    val packetLoss: Double,

    val averageLatency: Double,

    val bandwidth: Double,

    val activeConnections: Int
)