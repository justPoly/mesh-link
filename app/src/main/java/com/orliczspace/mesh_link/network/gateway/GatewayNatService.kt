package com.orliczspace.mesh_link.network.gateway

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class GatewayNatService(
    private val onMeshResponse: (ByteArray) -> Unit
) {

    companion object {
        private const val TAG = "GatewayNatService"
        private const val INTERNET_PORT = 0 // system assigned
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val socket = DatagramSocket(INTERNET_PORT)

    // Maps mesh-flow → internet endpoint
    private val natTable = ConcurrentHashMap<Int, InetAddress>()

    fun start() {
        listenForInternetResponses()
        Log.d(TAG, "Gateway NAT started")
    }

    fun stop() {
        scope.cancel()
        socket.close()
    }

    fun sendToInternet(meshPayload: ByteArray, destination: InetAddress) {
        val port = socket.localPort
        natTable[port] = destination

        val packet = DatagramPacket(
            meshPayload,
            meshPayload.size,
            destination,
            80 // Example: HTTP (you can make this dynamic)
        )

        socket.send(packet)
    }

    private fun listenForInternetResponses() {
        scope.launch {
            val buffer = ByteArray(65535)

            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    Log.d(TAG, "Internet response received")

                    onMeshResponse(
                        packet.data.copyOf(packet.length)
                    )

                } catch (e: Exception) {
                    if (isActive) {
                        Log.e(TAG, "NAT receive error: ${e.message}")
                    }
                }
            }
        }
    }
}
