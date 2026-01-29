package com.orliczspace.mesh_link.network.gateway

import android.content.Context
import android.util.Log

class SQLiteFlowLogger(context: Context) : IFlowLogger {

    private val dbHelper = FlowLogDbHelper(context)

    override fun logOutboundFlow(nodeId: String, entry: NatEntry) {
        Log.d("FlowLogger", "Outbound flow started for $nodeId")
    }

    override fun markFlowEnded(nodeId: String) {
        Log.d("FlowLogger", "Flow ended for $nodeId")
    }

    override fun persistMetrics(metrics: NatFlowMetrics) {
        dbHelper.insertCompletedFlow(metrics)
        Log.d(
            "FlowLogger",
            "Persisted flow metrics: node=${metrics.nodeId}, duration=${metrics.durationMs()}ms"
        )
    }
}
