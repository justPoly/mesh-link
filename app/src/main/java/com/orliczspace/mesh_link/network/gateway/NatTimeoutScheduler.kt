package com.orliczspace.mesh_link.network.gateway

import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NatTimeoutScheduler(
    private val natTable: MutableMap<String, NatEntry>,
    private val flowLogger: IFlowLogger,
    private val timeoutMillis: Long = 60_000L // 60 seconds
) {

    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    fun start() {
        scheduler.scheduleAtFixedRate(
            { cleanupExpiredFlows() },
            10,
            10,
            TimeUnit.SECONDS
        )
    }

    fun stop() {
        scheduler.shutdownNow()
    }

    private fun cleanupExpiredFlows() {
        val now = System.currentTimeMillis()

        val expiredNodes = natTable.filterValues { entry ->
            now - entry.lastActivityAt > timeoutMillis
        }.keys

        for (nodeId in expiredNodes) {
            natTable.remove(nodeId)
            flowLogger.markFlowEnded(nodeId)
            Log.d("NatTimeoutScheduler", "Expired NAT flow for node $nodeId")
        }
    }
}
