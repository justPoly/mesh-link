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
        const val COLUMN_ID = "id"
        const val COLUMN_NODE_ID = "node_id"
        const val COLUMN_SRC_IP = "src_ip"
        const val COLUMN_SRC_PORT = "src_port"
        const val COLUMN_DEST_IP = "dest_ip"
        const val COLUMN_DEST_PORT = "dest_port"
        const val COLUMN_START_TIMESTAMP = "start_timestamp"
        const val COLUMN_END_TIMESTAMP = "end_timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_FLOWS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NODE_ID TEXT,
                $COLUMN_SRC_IP TEXT,
                $COLUMN_SRC_PORT INTEGER,
                $COLUMN_DEST_IP TEXT,
                $COLUMN_DEST_PORT INTEGER,
                $COLUMN_START_TIMESTAMP INTEGER,
                $COLUMN_END_TIMESTAMP INTEGER
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FLOWS")
        onCreate(db)
    }

    /** Insert a new NAT flow */
    fun insertFlow(nodeId: String, entry: NatEntry) {
        val values = ContentValues().apply {
            put(COLUMN_NODE_ID, nodeId)
            put(COLUMN_SRC_IP, entry.srcIp)
            put(COLUMN_SRC_PORT, entry.srcPort)
            put(COLUMN_DEST_IP, entry.destIp)
            put(COLUMN_DEST_PORT, entry.destPort)
            put(COLUMN_START_TIMESTAMP, System.currentTimeMillis())
            put(COLUMN_END_TIMESTAMP, null as Long?)
        }

        writableDatabase.insert(TABLE_FLOWS, null, values)
    }

    /** Mark a flow as ended */
    fun markFlowEnded(nodeId: String) {
        val values = ContentValues().apply {
            put(COLUMN_END_TIMESTAMP, System.currentTimeMillis())
        }

        writableDatabase.update(
            TABLE_FLOWS,
            values,
            "$COLUMN_NODE_ID = ? AND $COLUMN_END_TIMESTAMP IS NULL",
            arrayOf(nodeId)
        )
    }
}
