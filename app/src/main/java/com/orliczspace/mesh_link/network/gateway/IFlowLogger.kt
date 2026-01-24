package com.orliczspace.mesh_link.network.gateway

interface IFlowLogger {
    fun logOutboundFlow(nodeId: String, entry: NatEntry)
    fun markFlowEnded(nodeId: String)
}
