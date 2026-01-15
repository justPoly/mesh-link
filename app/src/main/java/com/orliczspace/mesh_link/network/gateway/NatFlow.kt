package com.orliczspace.mesh_link.network.gateway

data class NatFlow(
    val sourceNodeId: String,
    val protocol: Int,
    val srcPort: Int,
    val dstIp: String,
    val dstPort: Int
)
