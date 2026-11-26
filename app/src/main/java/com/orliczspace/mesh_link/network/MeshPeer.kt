package com.orliczspace.mesh_link.network

enum class PeerConnectionState {
    DISCOVERED,
    CONNECTED,
    LOST
}

enum class MeshReachabilityState {
    NOT_TESTED,
    TESTING,
    REACHABLE,
    UNREACHABLE
}

data class MeshPeer(

    val nodeId: String,

    val deviceName: String = "Unknown device",

    val ipAddress: String,

    val meshPort: Int = 9999,

    val connectionState: PeerConnectionState =
        PeerConnectionState.DISCOVERED,

    val lastSeen: Long = System.currentTimeMillis(),

    val rssi: Int? = null,

    // Discovery latency measured on UDP port 8888.
    val latencyMs: Long? = null,

    val hasInternetAccess: Boolean = false,

    // Actual mesh transport reachability.
    val meshReachability: MeshReachabilityState =
        MeshReachabilityState.NOT_TESTED,

    // Round-trip latency measured on UDP port 9999.
    val meshLatencyMs: Long? = null,

    // Timestamp of the most recent completed mesh test.
    val lastMeshTestAt: Long? = null,

    val meshTestsSent: Long = 0L,

    val meshTestsSuccessful: Long = 0L,

    val meshTestsFailed: Long = 0L

) {

    val meshPacketLossPercent: Double?
        get() {

            val completedTests =
                meshTestsSuccessful + meshTestsFailed

            if (completedTests == 0L) {
                return null
            }

            return (
                    meshTestsFailed.toDouble() /
                            completedTests.toDouble()
                    ) * 100.0
        }
}
