package com.orliczspace.mesh_link.network.gateway

interface FlowLogger {
    fun onFlowStarted(entry: NatEntry)
    fun onFlowEnded(entry: NatEntry)
}
