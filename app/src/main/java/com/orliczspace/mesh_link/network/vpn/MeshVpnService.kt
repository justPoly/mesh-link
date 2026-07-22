package com.orliczspace.mesh_link.network.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.orliczspace.mesh_link.R
import com.orliczspace.mesh_link.network.PacketForwarder
import com.orliczspace.mesh_link.network.gateway.IpUdpPacketBuilder
import com.orliczspace.mesh_link.network.gateway.NatEntry
import com.orliczspace.mesh_link.network.packet.MeshPacket
import com.orliczspace.mesh_link.network.packet.PacketType
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream

class MeshVpnService : VpnService() {

    companion object {
        private const val TAG = "MeshVpnService"

        private const val CHANNEL_ID = "mesh_vpn_channel"
        private const val CHANNEL_NAME = "Mesh VPN"

        private const val NOTIFICATION_ID = 1001
    }

    private val scope =
        CoroutineScope(
            Dispatchers.IO + SupervisorJob()
        )

    private var tunInterface: ParcelFileDescriptor? = null
    private var tunInput: FileInputStream? = null
    private var tunOutput: FileOutputStream? = null

    private var packetForwarder: PacketForwarder? = null

    inner class LocalBinder : Binder() {
        fun getService(): MeshVpnService = this@MeshVpnService
    }

    override fun onBind(intent: Intent?): IBinder {
        return LocalBinder()
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            buildNotification()
        )

        Log.d(TAG, "Foreground VPN service started")
    }

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

        tunInput?.close()
        tunOutput?.close()
        tunInterface?.close()

        tunInput = null
        tunOutput = null
        tunInterface = null

        Log.d(TAG, "VPN service destroyed")
    }

    private fun setupVpn() {

        tunInterface =
            Builder()
                .setSession("MeshLink VPN")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .setBlocking(true)
                .establish()

        val fd = tunInterface!!.fileDescriptor

        tunInput = FileInputStream(fd)
        tunOutput = FileOutputStream(fd)

        Log.d(TAG, "VPN interface established")
    }

    private fun startTunReader() {

        val sourceNodeId =
            Build.MODEL ?: "unknown-node"

        scope.launch {

            val buffer = ByteArray(65535)

            while (isActive) {

                try {

                    val len =
                        tunInput?.read(buffer)
                            ?: break

                    if (len <= 0) continue

                    val rawPacket =
                        buffer.copyOf(len)

                    val meshPacket =
                        MeshPacket(
                            sourceNodeId = sourceNodeId,
                            destinationNodeId = "INTERNET",
                            payload = rawPacket,
                            type = PacketType.NAT_FORWARD,
                            ttl = 8,
                            requiresAck = false
                        )

                    packetForwarder?.forward(meshPacket)
                        ?: Log.w(
                            TAG,
                            "PacketForwarder not attached yet"
                        )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "TUN read error",
                        e
                    )

                    break

                }

            }

        }

    }

    fun writeToTun(packet: ByteArray) {

        val sourceNodeId =
            Build.MODEL ?: "unknown-node"

        try {

            val rebuilt =
                if (packet.size >= 20) {

                    IpUdpPacketBuilder.buildResponse(
                        NatEntry.createFromIpPacket(
                            packet,
                            sourceNodeId
                        ),
                        packet
                    )

                } else {

                    packet

                }

            tunOutput?.write(rebuilt)

        } catch (e: Exception) {

            Log.e(
                TAG,
                "TUN write error",
                e
            )

        }

    }

    fun attachPacketForwarder(
        forwarder: PacketForwarder
    ) {

        packetForwarder = forwarder

        Log.d(
            TAG,
            "PacketForwarder attached"
        )

    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                )

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(channel)

        }

    }

    private fun buildNotification(): Notification {

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("MeshLink VPN")
            .setContentText("Mesh network is active")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

    }

}