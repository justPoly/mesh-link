package com.orliczspace.mesh_link.network.gateway

data class NatFlowKey(
    val nodeId: String,
    val srcIp: String,
    val srcPort: Int,
    val destIp: String,
    val destPort: Int
)
