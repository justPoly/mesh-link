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

    /**
     * Start adaptive probing for a neighbour.
     */
    fun startProbing(
        nodeId: String,
        address: InetAddress,
        port: Int = 8888
    ) {
        stopProbing(nodeId)

        val job = scope.launch {
            while (isActive) {

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
     * Stop probing a specific neighbour.
     */
    fun stopProbing(nodeId: String) {
        probeJobs.remove(nodeId)?.cancel()
    }

    /**
     * Stop all probing activity.
     */
    fun stopAll() {
        probeJobs.values.forEach { it.cancel() }
        probeJobs.clear()
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
            avgRtt == null -> 2000L                 // New / unknown link
            stability > 100 -> 3000L               // Unstable
            stability in 40.0..100.0 -> 6000L      // Moderate
            else -> 10000L                         // Stable
        }
    }
}
