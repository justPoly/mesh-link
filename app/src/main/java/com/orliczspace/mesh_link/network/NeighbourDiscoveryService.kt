package com.orliczspace.mesh_link.network

import android.Manifest
import android.content.*
import android.net.wifi.p2p.*
import android.util.Log
import androidx.compose.runtime.mutableStateListOf

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
                    manager.requestPeers(channel, peerListListener)
                }
            }
        }
    }

    /**
     * Starts Wi-Fi Direct peer discovery.
     * Should be called once (e.g., in LaunchedEffect).
     */
    fun startDiscovery() {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        }

        context.registerReceiver(receiver, filter)

        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("NeighbourDiscovery", "Peer discovery started")
            }

            override fun onFailure(reason: Int) {
                Log.e("NeighbourDiscovery", "Discovery failed: $reason")
            }
        })
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
            Log.w("NeighbourDiscovery", "Receiver already unregistered")
        }
    }
}
