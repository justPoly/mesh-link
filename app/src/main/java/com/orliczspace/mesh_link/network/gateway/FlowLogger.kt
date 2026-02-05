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

    override fun persistMetrics(metrics: NatFlowMetrics) {
        // Simple implementation — you can replace with DB or file persistence later
        Log.d("FlowLogger", "Persisting NAT flow metrics: $metrics")
    }

    fun getAllFlows(): List<NatEntry> = flowHistory.toList()
}
