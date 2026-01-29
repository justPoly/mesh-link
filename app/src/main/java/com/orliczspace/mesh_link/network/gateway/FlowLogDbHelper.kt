package com.orliczspace.mesh_link.network.gateway

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

class FlowLogDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "flow_logs.db"
        private const val DATABASE_VERSION = 2

        const val TABLE_FLOWS = "flows"

        const val COL_ID = "id"
        const val COL_NODE_ID = "node_id"
        const val COL_SRC_IP = "src_ip"
        const val COL_SRC_PORT = "src_port"
        const val COL_DEST_IP = "dest_ip"
        const val COL_DEST_PORT = "dest_port"

        const val COL_PKTS_OUT = "pkts_out"
        const val COL_PKTS_IN = "pkts_in"
        const val COL_BYTES_OUT = "bytes_out"
        const val COL_BYTES_IN = "bytes_in"

        const val COL_START_TS = "start_ts"
        const val COL_END_TS = "end_ts"
        const val COL_DURATION = "duration_ms"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val sql = """
            CREATE TABLE $TABLE_FLOWS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NODE_ID TEXT,
                $COL_SRC_IP TEXT,
                $COL_SRC_PORT INTEGER,
                $COL_DEST_IP TEXT,
                $COL_DEST_PORT INTEGER,
                $COL_PKTS_OUT INTEGER,
                $COL_PKTS_IN INTEGER,
                $COL_BYTES_OUT INTEGER,
                $COL_BYTES_IN INTEGER,
                $COL_START_TS INTEGER,
                $COL_END_TS INTEGER,
                $COL_DURATION INTEGER
            )
        """.trimIndent()

        db.execSQL(sql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FLOWS")
        onCreate(db)
    }

    fun insertCompletedFlow(metrics: NatFlowMetrics) {
        val values = ContentValues().apply {
            put(COL_NODE_ID, metrics.nodeId)
            put(COL_SRC_IP, metrics.srcIp)
            put(COL_SRC_PORT, metrics.srcPort)
            put(COL_DEST_IP, metrics.destIp)
            put(COL_DEST_PORT, metrics.destPort)

            put(COL_PKTS_OUT, metrics.packetsOut)
            put(COL_PKTS_IN, metrics.packetsIn)
            put(COL_BYTES_OUT, metrics.bytesOut)
            put(COL_BYTES_IN, metrics.bytesIn)

            put(COL_START_TS, metrics.startTime)
            put(COL_END_TS, metrics.lastSeen)
            put(COL_DURATION, metrics.durationMs())
        }

        writableDatabase.insert(TABLE_FLOWS, null, values)
    }
}
