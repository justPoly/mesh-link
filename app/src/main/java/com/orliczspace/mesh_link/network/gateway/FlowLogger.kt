package com.orliczspace.mesh_link.network.gateway

import android.util.Log

class FlowLogger : IFlowLogger {
    private val flowHistory = mutableListOf<NatEntry>()

    override fun logOutboundFlow(nodeId: String, entry: NatEntry) {
        flowHistory.add(entry)
        Log.d("FlowLogger", "Outbound flow logged for node $nodeId: ${entry.destIp}:${entry.destPort}")
    }

    override fun markFlowEnded(nodeId: String) {
        Log.d("FlowLogger", "Flow ended for node $nodeId")
    }

    fun getAllFlows(): List<NatEntry> = flowHistory.toList()
}
