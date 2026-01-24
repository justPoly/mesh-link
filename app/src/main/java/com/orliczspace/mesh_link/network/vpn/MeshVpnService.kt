package com.orliczspace.mesh_link.network.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.orliczspace.mesh_link.network.ForwardPacket
import com.orliczspace.mesh_link.network.PacketForwarder
import com.orliczspace.mesh_link.network.gateway.IpUdpPacketBuilder
import com.orliczspace.mesh_link.network.gateway.NatEntry
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream

class MeshVpnService : VpnService() {

    companion object {
        private const val TAG = "MeshVpnService"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var tunInterface: ParcelFileDescriptor? = null
    private var tunInput: FileInputStream? = null
    private var tunOutput: FileOutputStream? = null

    private var packetForwarder: PacketForwarder? = null

    inner class LocalBinder : Binder() {
        fun getService(): MeshVpnService = this@MeshVpnService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (tunInterface == null) {
            setupVpn()
            startTunReader()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        tunInput?.close()
        tunOutput?.close()
        tunInterface?.close()
        tunInput = null
        tunOutput = null
        tunInterface = null
    }

    private fun setupVpn() {
        val builder = Builder()
            .setSession("MeshLink VPN")
            .addAddress("10.0.0.2", 32)
            .addRoute("10.0.0.0", 8)
            .setBlocking(true)

        tunInterface = builder.establish() ?: throw IllegalStateException("VPN establish failed")

        val fd = tunInterface!!.fileDescriptor
        tunInput = FileInputStream(fd)
        tunOutput = FileOutputStream(fd)
        Log.d(TAG, "VPN interface established")
    }

    private fun startTunReader() {
        scope.launch {
            val buffer = ByteArray(65535)
            while (isActive) {
                try {
                    val len = tunInput?.read(buffer) ?: break
                    if (len <= 0) continue

                    val rawPacket = buffer.copyOf(len)

                    val forwardPacket = ForwardPacket(
                        sourceNodeId = android.os.Build.MODEL ?: "unknown-node",
                        destinationNodeId = null,
                        ttl = 8,
                        payload = rawPacket
                    )

                    // ✅ Safe call to send
                    packetForwarder?.let { forwarder ->
                        forwarder.send(forwardPacket)
                    } ?: Log.w(TAG, "PacketForwarder not attached yet")

                } catch (e: Exception) {
                    Log.e(TAG, "TUN read error", e)
                    break
                }
            }
        }
    }

    fun writeToTun(packet: ByteArray) {
        try {
            if (packet.size >= 20) {
                val rebuilt = IpUdpPacketBuilder.buildResponse(NatEntry.createFromIpPacket(packet), packet)
                tunOutput?.write(rebuilt)
            } else {
                tunOutput?.write(packet)
            }
        } catch (e: Exception) {
            Log.e(TAG, "TUN write error", e)
        }
    }

    fun attachPacketForwarder(forwarder: PacketForwarder) {
        packetForwarder = forwarder
        Log.d(TAG, "PacketForwarder attached")
    }
}
