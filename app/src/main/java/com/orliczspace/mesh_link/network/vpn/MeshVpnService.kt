package com.orliczspace.mesh_link.network.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.orliczspace.mesh_link.network.PacketForwarder
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

    /* ---------------- Binder ---------------- */

    inner class LocalBinder : Binder() {
        fun getService(): MeshVpnService = this@MeshVpnService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    /* ---------------- Lifecycle ---------------- */

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (tunInterface == null) {
            setupVpn()
            startTunReader()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        tunInterface?.close()
    }

    /* ---------------- VPN Setup ---------------- */

    private fun setupVpn() {
        val builder = Builder()
            .setSession("MeshLink VPN")
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .setBlocking(true)

        tunInterface = builder.establish()

        val fd = tunInterface?.fileDescriptor
            ?: throw IllegalStateException("VPN establish failed")

        tunInput = FileInputStream(fd)
        tunOutput = FileOutputStream(fd)

        Log.d(TAG, "VPN interface established")
    }

    /* ---------------- TUN IO ---------------- */

    private fun startTunReader() {
        scope.launch {
            val buffer = ByteArray(32767)

            while (isActive) {
                try {
                    val len = tunInput?.read(buffer) ?: break
                    if (len > 0) {
                        packetForwarder?.forwardRawIpPacket(buffer.copyOf(len))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "TUN read error: ${e.message}")
                    break
                }
            }
        }
    }

    fun writeToTun(packet: ByteArray) {
        try {
            tunOutput?.write(packet)
        } catch (e: Exception) {
            Log.e(TAG, "TUN write error: ${e.message}")
        }
    }

    fun attachPacketForwarder(forwarder: PacketForwarder) {
        packetForwarder = forwarder
    }
}
