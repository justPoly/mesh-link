package com.orliczspace.mesh_link.network.gateway

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

class FlowLogDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "mesh_flows.db"
        private const val DATABASE_VERSION = 1
        const val TABLE_NAME = "flow_logs"
        const val COL_ID = "id"
        const val COL_NODE = "node_id"
        const val COL_SRC_IP = "src_ip"
        const val COL_SRC_PORT = "src_port"
        const val COL_DEST_IP = "dest_ip"
        const val COL_DEST_PORT = "dest_port"
        const val COL_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NODE TEXT,
                $COL_SRC_IP TEXT,
                $COL_SRC_PORT INTEGER,
                $COL_DEST_IP TEXT,
                $COL_DEST_PORT INTEGER,
                $COL_TIMESTAMP INTEGER
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertFlow(nodeId: String, natEntry: NatEntry) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_NODE, nodeId)
            put(COL_SRC_IP, natEntry.srcIp)
            put(COL_SRC_PORT, natEntry.srcPort)
            put(COL_DEST_IP, natEntry.destIp)
            put(COL_DEST_PORT, natEntry.destPort)
            put(COL_TIMESTAMP, System.currentTimeMillis())
        }
        db.insert(TABLE_NAME, null, values)
    }

    fun markFlowEnded(entry: NatEntry) {
        val db = writableDatabase
        db.execSQL(
            "UPDATE flow_logs SET ended_at = CURRENT_TIMESTAMP WHERE src_ip=? AND src_port=? AND dest_ip=? AND dest_port=?",
            arrayOf(entry.srcIp, entry.srcPort, entry.destIp, entry.destPort)
        )
    }
}
