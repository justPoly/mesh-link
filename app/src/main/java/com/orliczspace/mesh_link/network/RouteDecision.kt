package com.orliczspace.mesh_link.network

data class RouteDecision(
    val destination: String,      // e.g. "INTERNET"
    val nextHopNodeId: String,
    val viaGateway: Boolean
)
