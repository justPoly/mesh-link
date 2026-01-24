package com.orliczspace.mesh_link.network.gateway

import android.content.Context

class SQLiteFlowLogger(context: Context) : FlowLogger {

    private val dbHelper = FlowLogDbHelper(context)

    override fun onFlowStarted(entry: NatEntry) {
        dbHelper.insertFlow(entry.sourceNodeId ?: "unknown", entry)
    }

    override fun onFlowEnded(entry: NatEntry) {
        dbHelper.markFlowEnded(entry)
    }
}
