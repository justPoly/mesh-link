package com.orliczspace.mesh_link.network.gateway

import android.content.Context
import android.database.Cursor
import android.util.Log

class FlowLogger(context: Context) {

    private val dbHelper = FlowLogDbHelper(context)

    /** Log an outbound flow to SQLite */
    fun logOutboundFlow(nodeId: String, entry: NatEntry) {
        dbHelper.insertFlow(nodeId, entry)
        Log.d("FlowLogger", "Outbound flow logged for node $nodeId: ${entry.destIp}:${entry.destPort}")
    }

    /** Mark flow ended (optional timestamp or cleanup logic) */
    fun markFlowEnded(nodeId: String) {
        Log.d("FlowLogger", "Flow ended for node $nodeId")
    }

    /** Fetch all flows from SQLite */
    fun getAllFlows(): List<NatEntry> {
        val db = dbHelper.readableDatabase
        val flows = mutableListOf<NatEntry>()

        val cursor: Cursor = db.query(
            FlowLogDbHelper.TABLE_FLOWS,
            arrayOf(
                FlowLogDbHelper.COLUMN_SRC_IP,
                FlowLogDbHelper.COLUMN_SRC_PORT,
                FlowLogDbHelper.COLUMN_DEST_IP,
                FlowLogDbHelper.COLUMN_DEST_PORT,
                FlowLogDbHelper.COLUMN_NODE_ID
            ),
            null,
            null,
            null,
            null,
            "${FlowLogDbHelper.COLUMN_TIMESTAMP} DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                val srcIp = it.getString(it.getColumnIndexOrThrow(FlowLogDbHelper.COLUMN_SRC_IP))
                val srcPort = it.getInt(it.getColumnIndexOrThrow(FlowLogDbHelper.COLUMN_SRC_PORT))
                val destIp = it.getString(it.getColumnIndexOrThrow(FlowLogDbHelper.COLUMN_DEST_IP))
                val destPort = it.getInt(it.getColumnIndexOrThrow(FlowLogDbHelper.COLUMN_DEST_PORT))
                val nodeId = it.getString(it.getColumnIndexOrThrow(FlowLogDbHelper.COLUMN_NODE_ID))

                flows.add(
                    NatEntry(
                        srcIp = srcIp,
                        srcPort = srcPort,
                        destIp = destIp,
                        destPort = destPort,
                        payload = ByteArray(0), // payload not stored historically
                        sourceNodeId = nodeId
                    )
                )
            }
        }
        return flows
    }
}
