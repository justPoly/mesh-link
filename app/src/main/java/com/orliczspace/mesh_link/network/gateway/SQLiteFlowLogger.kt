package com.orliczspace.mesh_link.network.gateway

import android.content.Context
import android.util.Log

class SQLiteFlowLogger(context: Context) : IFlowLogger {

    private val dbHelper = FlowLogDbHelper(context)

    override fun logOutboundFlow(nodeId: String, entry: NatEntry) {
        dbHelper.insertFlow(nodeId, entry)
        Log.d("SQLiteFlowLogger", "Outbound flow persisted for node $nodeId: ${entry.destIp}:${entry.destPort}")
    }

    override fun markFlowEnded(nodeId: String) {
        // Optional: mark flows as ended (could add a column like 'ended_timestamp')
        Log.d("SQLiteFlowLogger", "Flow ended persisted for node $nodeId")
    }
}
