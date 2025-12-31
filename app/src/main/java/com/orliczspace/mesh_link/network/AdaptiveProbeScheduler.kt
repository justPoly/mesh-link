package com.orliczspace.mesh_link.network

import android.util.Log
import kotlinx.coroutines.*
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Dynamically schedules probe intervals per neighbour
 * based on link stability and responsiveness.
 */
class AdaptiveProbeScheduler(
    private val linkProbeService: LinkProbeService
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Track probe jobs per neighbour
    private val probeJobs = ConcurrentHashMap<String, Job>()

    // Last successful response per node
    private val lastResponseTime = ConcurrentHashMap<String, Long>()

    /**
     * Start adaptive probing for a neighbour.
     */
    fun startProbing(
        nodeId: String,
        address: InetAddress,
        port: Int = 8888
    ) {
        if (probeJobs.containsKey(nodeId)) return

        val job = scope.launch {
            while (isActive) {

                val now = System.currentTimeMillis()
                val lastSeen = lastResponseTime[nodeId]

                // Dead peer detection (30s timeout)
                if (lastSeen != null && now - lastSeen > 30_000) {
                    Log.d("AdaptiveProbe", "Peer $nodeId timed out, stopping probes")
                    stopProbing(nodeId)
                    return@launch
                }

                linkProbeService.sendProbe(address, port)

                val avgRtt = linkProbeService.getAverageRtt(nodeId)
                val stability = linkProbeService.getStability(nodeId)

                val delayMs = calculateNextInterval(avgRtt, stability)

                Log.d(
                    "AdaptiveProbe",
                    "Node=$nodeId | RTT=$avgRtt | Stability=$stability | Next=$delayMs ms"
                )

                delay(delayMs)
            }
        }

        probeJobs[nodeId] = job
    }

    /**
     * Called by LinkProbeService when a probe response is received.
     */
    fun notifyResponse(nodeId: String) {
        lastResponseTime[nodeId] = System.currentTimeMillis()
    }

    /**
     * Stop probing a specific neighbour.
     */
    fun stopProbing(nodeId: String) {
        probeJobs.remove(nodeId)?.cancel()
        lastResponseTime.remove(nodeId)
    }

    /**
     * Stop all probing activity.
     */
    fun stopAll() {
        probeJobs.values.forEach { it.cancel() }
        probeJobs.clear()
        lastResponseTime.clear()
        scope.cancel()
    }

    /**
     * Adaptive interval logic.
     */
    private fun calculateNextInterval(
        avgRtt: Long?,
        stability: Double
    ): Long {
        return when {
            avgRtt == null -> 2_000L
            stability > 100 -> 3_000L
            stability in 40.0..100.0 -> 6_000L
            else -> 10_000L
        }
    }
}
