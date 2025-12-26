package com.orliczspace.mesh_link.network

import kotlinx.coroutines.*
import java.net.InetAddress

/**
 * Automatically sends probes to all connected peers.
 */
class ProbeCoordinator(
    private val probeService: LinkProbeService
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startProbing(peers: List<MeshPeer>) {
        scope.launch {
            while (isActive) {
                peers.forEach { peer ->
                    try {
                        val address = InetAddress.getByName(peer.ipAddress)
                        probeService.sendProbe(address, 8888)
                    } catch (_: Exception) {
                        // Peer unreachable
                    }
                }
                delay(3_000) // probe interval (3 seconds)
            }
        }
    }

    fun stop() {
        scope.cancel()
    }
}
