package com.orliczspace.mesh_link.network

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.wifi.p2p.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.ContextCompat

/**
 * Handles peer discovery using Wi-Fi Direct (Wi-Fi P2P).
 * Exposes discovered peers as a Compose-observable list.
 */
class NeighbourDiscoveryService(
    private val context: Context
) {

    private val manager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager

    private val channel =
        manager.initialize(context, context.mainLooper, null)

    // Compose-observable list of discovered device names
    val discoveredPeers = mutableStateListOf<String>()

    private val peerListListener = WifiP2pManager.PeerListListener { peers ->
        discoveredPeers.clear()
        peers.deviceList.forEach { device ->
            discoveredPeers.add(device.deviceName ?: "Unknown Device")
        }

        Log.d("NeighbourDiscovery", "Peers found: ${discoveredPeers.size}")
    }

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
                    Log.d("NeighbourDiscovery", "Peer list changed")
                    // Safely request peers only if permission is granted
                    if (hasRequiredPermissions()) {
                        manager.requestPeers(channel, peerListListener)
                    }
                }
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val locationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val nearbyPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed on older versions
        }

        return locationPermission && nearbyPermission
    }

    /**
     * Starts Wi-Fi Direct peer discovery.
     * Should be called once (e.g., in LaunchedEffect).
     */
    fun startDiscovery() {
        if (!hasRequiredPermissions()) {
            Log.e("NeighbourDiscovery", "Missing required permissions for Wi-Fi Direct")
            // You should request permissions from your Activity/ViewModel here
            return
        }

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        }

        context.registerReceiver(receiver, filter)

        try {
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("NeighbourDiscovery", "Peer discovery started successfully")
                }

                override fun onFailure(reason: Int) {
                    Log.e("NeighbourDiscovery", "Discovery failed: reason code $reason")
                    // Reason codes: 0 = ERROR, 1 = P2P_UNSUPPORTED, 2 = BUSY
                }
            })
        } catch (e: SecurityException) {
            Log.e("NeighbourDiscovery", "SecurityException during discoverPeers: ${e.message}")
            // Handle denied permission at runtime (e.g., inform user)
        }
    }

    /**
     * Stops discovery and unregisters the broadcast receiver.
     * MUST be called in Activity.onDestroy().
     */
    fun stopDiscovery() {
        try {
            context.unregisterReceiver(receiver)
            Log.d("NeighbourDiscovery", "Receiver unregistered")
        } catch (e: Exception) {
            Log.w("NeighbourDiscovery", "Receiver already unregistered or error: ${e.message}")
        }
    }
}