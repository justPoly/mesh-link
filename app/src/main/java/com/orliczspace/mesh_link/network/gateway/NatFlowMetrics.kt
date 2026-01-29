package com.orliczspace.mesh_link.network.gateway

data class NatFlowMetrics(
    val nodeId: String,
    val srcIp: String,
    val srcPort: Int,
    val destIp: String,
    val destPort: Int,
    val startTime: Long = System.currentTimeMillis()
) {
    var lastSeen: Long = startTime

    var packetsOut: Int = 0
    var packetsIn: Int = 0
    var bytesOut: Long = 0
    var bytesIn: Long = 0

    // RTT metrics (milliseconds)
    var lastRtt: Long = 0
    var minRtt: Long = Long.MAX_VALUE
    var maxRtt: Long = 0
    private var totalRtt: Long = 0
    private var rttSamples: Int = 0

    fun recordOutbound(bytes: Int) {
        packetsOut++
        bytesOut += bytes
        lastSeen = System.currentTimeMillis()
    }

    fun recordInbound(bytes: Int, rttMs: Long) {
        packetsIn++
        bytesIn += bytes
        lastSeen = System.currentTimeMillis()

        lastRtt = rttMs
        minRtt = minOf(minRtt, rttMs)
        maxRtt = maxOf(maxRtt, rttMs)

        totalRtt += rttMs
        rttSamples++
    }

    fun averageRtt(): Long =
        if (rttSamples == 0) 0 else totalRtt / rttSamples

    fun durationMs(): Long = lastSeen - startTime
}
