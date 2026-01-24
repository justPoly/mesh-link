package com.orliczspace.mesh_link.network.gateway

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

class FlowLogDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "flow_logs.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_FLOWS = "flows"
        const val COLUMN_ID = "id"
        const val COLUMN_NODE_ID = "node_id"
        const val COLUMN_SRC_IP = "src_ip"
        const val COLUMN_SRC_PORT = "src_port"
        const val COLUMN_DEST_IP = "dest_ip"
        const val COLUMN_DEST_PORT = "dest_port"
        const val COLUMN_TIMESTAMP = "timestamp"
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
                $COLUMN_TIMESTAMP INTEGER
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FLOWS")
        onCreate(db)
    }

    /** Insert a new flow log into the database */
    fun insertFlow(nodeId: String, entry: NatEntry) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NODE_ID, nodeId)
            put(COLUMN_SRC_IP, entry.srcIp)
            put(COLUMN_SRC_PORT, entry.srcPort)
            put(COLUMN_DEST_IP, entry.destIp)
            put(COLUMN_DEST_PORT, entry.destPort)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
        }
        db.insert(TABLE_FLOWS, null, values)
    }
}
