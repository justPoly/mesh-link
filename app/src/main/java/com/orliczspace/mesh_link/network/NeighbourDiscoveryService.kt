package com.orliczspace.mesh_link.network

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.*
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.ContextCompat

/**
 * Handles peer discovery, connection, and group information
 * using Wi-Fi Direct (Wi-Fi P2P).
 *
 * Exposes:
 *  - discoveredPeers  → UI discovery
 *  - connectedPeers   → routing / probing layer
 */
class NeighbourDiscoveryService(
    private val context: Context
) {

    private val manager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager

    private val channel =
        manager.initialize(context, context.mainLooper, null)

    /* ---------------- DISCOVERY ---------------- */

    /** Device names only (UI-level) */
    val discoveredPeers = mutableStateListOf<String>()

    private val peerListListener = WifiP2pManager.PeerListListener { peers ->
        discoveredPeers.clear()
        peers.deviceList.forEach { device ->
            discoveredPeers.add(device.deviceName ?: "Unknown Device")
        }
        Log.d("NeighbourDiscovery", "Peers found: ${discoveredPeers.size}")
    }

    /* ---------------- ROUTING-LEVEL STATE ---------------- */

    /**
     * Connected peers with real IP addresses.
     * This is what LinkProbeService consumes.
     *
     * key   → nodeId (string)
     * value → IP address
     */
    val connectedPeers = mutableStateMapOf<String, String>()

    private val connectionInfoListener =
        WifiP2pManager.ConnectionInfoListener { info ->

            if (!info.groupFormed) return@ConnectionInfoListener

            val groupOwnerIp = info.groupOwnerAddress?.hostAddress ?: return@ConnectionInfoListener
            val role = if (info.isGroupOwner) "GO" else "CLIENT"

            connectedPeers.clear()
            connectedPeers[role] = groupOwnerIp

            Log.d(
                "NeighbourDiscovery",
                "Connected → role=$role | ip=$groupOwnerIp"
            )
        }

    /* ---------------- BROADCAST RECEIVER ---------------- */

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            when (intent?.action) {

                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(
                        WifiP2pManager.EXTRA_WIFI_STATE,
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED
                    )
                    Log.d("NeighbourDiscovery", "Wi-Fi P2P state: $state")
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    if (hasRequiredPermissions()) {
                        try {
                            manager.requestPeers(channel, peerListListener)
                        } catch (e: SecurityException) {
                            Log.e("NeighbourDiscovery", "requestPeers denied")
                        }
                    }
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo =
                        intent.getParcelableExtra<NetworkInfo>(
                            WifiP2pManager.EXTRA_NETWORK_INFO
                        )

                    if (networkInfo?.isConnected == true && hasRequiredPermissions()) {
                        try {
                            manager.requestConnectionInfo(channel, connectionInfoListener)
                        } catch (e: SecurityException) {
                            Log.e("NeighbourDiscovery", "requestConnectionInfo denied")
                        }
                    }
                }
            }
        }
    }

    /* ---------------- PERMISSIONS ---------------- */

    private fun hasRequiredPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val nearbyPermission =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) == PackageManager.PERMISSION_GRANTED
            } else true

        return fineLocation && nearbyPermission
    }

    /* ---------------- START DISCOVERY ---------------- */

    fun startDiscovery() {
        if (!hasRequiredPermissions()) {
            Log.e("NeighbourDiscovery", "Missing Wi-Fi Direct permissions")
            return
        }

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }

        context.registerReceiver(receiver, filter)

        try {
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("NeighbourDiscovery", "Discovery started")
                }

                override fun onFailure(reason: Int) {
                    Log.e("NeighbourDiscovery", "Discovery failed: $reason")
                }
            })
        } catch (e: SecurityException) {
            Log.e("NeighbourDiscovery", "discoverPeers denied")
        }
    }

    /* ---------------- CONNECT ---------------- */

    fun connectToPeer(device: WifiP2pDevice) {
        if (!hasRequiredPermissions()) return

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }

        try {
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("NeighbourDiscovery", "Connection initiated")
                }

                override fun onFailure(reason: Int) {
                    Log.e("NeighbourDiscovery", "Connection failed: $reason")
                }
            })
        } catch (e: SecurityException) {
            Log.e("NeighbourDiscovery", "connect denied")
        }
    }

    /* ---------------- STOP ---------------- */

    fun stopDiscovery() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
    }
}
