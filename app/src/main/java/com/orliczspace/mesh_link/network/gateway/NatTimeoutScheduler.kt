package com.orliczspace.mesh_link.network.gateway

import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Periodically removes expired NAT flows based on inactivity.
 */
class NatTimeoutScheduler(
    private val natTable: MutableMap<String, NatEntry>,
    private val flowLogger: IFlowLogger,
    private val timeoutMillis: Long = 60_000L // 60 seconds idle timeout
) {

    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    /**
     * Start periodic NAT cleanup
     */
    fun start() {
        scheduler.scheduleAtFixedRate(
            { cleanupExpiredFlows() },
            10,
            10,
            TimeUnit.SECONDS
        )
    }

    /**
     * Stop the scheduler
     */
    fun stop() {
        scheduler.shutdownNow()
    }

    /**
     * Remove flows that have been idle for longer than timeoutMillis
     */
    private fun cleanupExpiredFlows() {
        val now = System.currentTimeMillis()

        val expiredNodeIds = natTable
            .filterValues { entry ->
                now - entry.lastActivityAt > timeoutMillis
            }
            .keys
            .toList() // avoid concurrent modification

        for (nodeId in expiredNodeIds) {
            natTable.remove(nodeId)
            flowLogger.markFlowEnded(nodeId)
            Log.d("NatTimeoutScheduler", "Expired NAT flow for node $nodeId")
        }
    }
}
