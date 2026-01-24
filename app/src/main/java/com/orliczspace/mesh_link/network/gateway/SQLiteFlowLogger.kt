package com.orliczspace.mesh_link.network.gateway

import android.content.Context
import android.util.Log

class SQLiteFlowLogger(context: Context) : IFlowLogger {

    private val dbHelper = FlowLogDbHelper(context)

    override fun logOutboundFlow(nodeId: String, entry: NatEntry) {
        dbHelper.insertFlow(nodeId, entry)
        Log.d(
            "SQLiteFlowLogger",
            "Flow started: ${entry.srcIp}:${entry.srcPort} → ${entry.destIp}:${entry.destPort}"
        )
    }

    override fun markFlowEnded(nodeId: String) {
        dbHelper.markFlowEnded(nodeId)
        Log.d("SQLiteFlowLogger", "Flow ended for node $nodeId")
    }
}
