package com.orliczspace.mesh_link.network

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * Handles peer discovery, connection, and group information
 * using Wi-Fi Direct (Wi-Fi P2P).
 *
 * Exposes:
 * - discoveredPeers: devices discovered through Wi-Fi Direct
 * - connectedPeers: Wi-Fi Direct group connection information
 * - discoveryStatus: observable peer-discovery lifecycle
 */
enum class PeerDiscoveryStatus {
    INITIALIZING,
    DISCOVERING,
    PEERS_FOUND,
    FAILED,
    STOPPED
}


class NeighbourDiscoveryService(
    private val context: Context
) {

    companion object {
        private const val TAG = "NeighbourDiscovery"
    }

    /**
     * Persistent identity for this Mesh-link installation.
     * Generated once and reused across app restarts.
     */
    val localNodeId: String by lazy {
        NodeIdentity.getOrCreate(context.applicationContext)
    }

    var discoveryStatus by mutableStateOf(
        PeerDiscoveryStatus.INITIALIZING
    )
        private set

    private val manager =
        context.getSystemService(
            Context.WIFI_P2P_SERVICE
        ) as WifiP2pManager

    private val channel =
        manager.initialize(
            context,
            context.mainLooper,
            null
        )

    // ---------------------------------------------------------
    // DISCOVERY STATE
    // ---------------------------------------------------------

    /**
     * Device names discovered through Wi-Fi Direct.
     */
    val discoveredPeers = mutableStateListOf<String>()

    private val peerListListener =
        WifiP2pManager.PeerListListener { peers ->

            val currentPeers = peers.deviceList.map { device ->
                device.deviceName ?: "Unknown Device"
            }

            discoveredPeers.clear()
            discoveredPeers.addAll(currentPeers)

            discoveryStatus =
                if (currentPeers.isNotEmpty()) {
                    PeerDiscoveryStatus.PEERS_FOUND
                } else {
                    PeerDiscoveryStatus.DISCOVERING
                }

            Log.d(
                TAG,
                "Peers found: ${discoveredPeers.size}"
            )
        }

    // ---------------------------------------------------------
    // ROUTING-LEVEL STATE
    // ---------------------------------------------------------

    /**
     * Wi-Fi Direct group information.
     *
     * Key: role (GO or CLIENT)
     * Value: group owner's IP address
     *
     * This is connection information, not a complete list
     * of every connected mesh node.
     */
    val connectedPeers =
        mutableStateMapOf<String, String>()

    private val connectionInfoListener =
        WifiP2pManager.ConnectionInfoListener { info ->

            if (!info.groupFormed) {
                Log.d(
                    TAG,
                    "Wi-Fi Direct group is not formed"
                )
                return@ConnectionInfoListener
            }

            val groupOwnerIp =
                info.groupOwnerAddress?.hostAddress
                    ?: return@ConnectionInfoListener

            val role =
                if (info.isGroupOwner) {
                    "GO"
                } else {
                    "CLIENT"
                }

            val previousConnection =
                connectedPeers[role]

            connectedPeers.clear()
            connectedPeers[role] = groupOwnerIp

            if (previousConnection != groupOwnerIp) {
                NetworkActivityLog.record(
                    message = "Wi-Fi Direct connected",
                    detail = "$role @ $groupOwnerIp"
                )
            }

            Log.d(
                TAG,
                "Connected → role=$role | ip=$groupOwnerIp"
            )
        }

    // ---------------------------------------------------------
    // BROADCAST RECEIVER
    // ---------------------------------------------------------

    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(
            context: Context?,
            intent: Intent?
        ) {

            when (intent?.action) {

                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {

                    val state = intent.getIntExtra(
                        WifiP2pManager.EXTRA_WIFI_STATE,
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED
                    )

                    Log.d(
                        TAG,
                        "Wi-Fi P2P state: $state"
                    )
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {

                    if (hasRequiredPermissions()) {
                        try {
                            manager.requestPeers(
                                channel,
                                peerListListener
                            )
                        } catch (e: SecurityException) {
                            discoveryStatus =
                                PeerDiscoveryStatus.FAILED

                            Log.e(
                                TAG,
                                "requestPeers denied",
                                e
                            )
                        }
                    }
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {

                    val networkInfo =
                        intent.getParcelableExtra<NetworkInfo>(
                            WifiP2pManager.EXTRA_NETWORK_INFO
                        )

                    if (
                        networkInfo?.isConnected == true &&
                        hasRequiredPermissions()
                    ) {
                        try {
                            manager.requestConnectionInfo(
                                channel,
                                connectionInfoListener
                            )
                        } catch (e: SecurityException) {
                            Log.e(
                                TAG,
                                "requestConnectionInfo denied",
                                e
                            )
                        }
                    } else {
                        connectedPeers.clear()

                        Log.d(
                            TAG,
                            "Wi-Fi Direct connection unavailable"
                        )
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------
    // PERMISSIONS
    // ---------------------------------------------------------

    private fun hasRequiredPermissions(): Boolean {

        val fineLocation =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val nearbyPermission =
            if (
                android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.TIRAMISU
            ) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        return fineLocation && nearbyPermission
    }

    // ---------------------------------------------------------
    // START DISCOVERY
    // ---------------------------------------------------------

    fun startDiscovery() {

        if (!hasRequiredPermissions()) {
            discoveryStatus =
                PeerDiscoveryStatus.FAILED

            Log.e(
                TAG,
                "Missing Wi-Fi Direct permissions"
            )
            return
        }

        discoveryStatus =
            PeerDiscoveryStatus.DISCOVERING

        Log.d(
            TAG,
            "Local Mesh-link Node ID: $localNodeId"
        )

        val filter = IntentFilter().apply {
            addAction(
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION
            )
            addAction(
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
            )
            addAction(
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
            )
        }

        try {
            context.registerReceiver(
                receiver,
                filter
            )
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Receiver registration: ${e.message}"
            )
        }

        try {
            manager.discoverPeers(
                channel,
                object : WifiP2pManager.ActionListener {

                    override fun onSuccess() {
                        discoveryStatus =
                            if (discoveredPeers.isNotEmpty()) {
                                PeerDiscoveryStatus.PEERS_FOUND
                            } else {
                                PeerDiscoveryStatus.DISCOVERING
                            }

                        Log.d(
                            TAG,
                            "Discovery started"
                        )
                    }

                    override fun onFailure(reason: Int) {
                        discoveryStatus =
                            PeerDiscoveryStatus.FAILED

                        Log.e(
                            TAG,
                            "Discovery failed: $reason"
                        )
                    }
                }
            )
        } catch (e: SecurityException) {
            discoveryStatus =
                PeerDiscoveryStatus.FAILED

            Log.e(
                TAG,
                "discoverPeers denied",
                e
            )
        }
    }

    // ---------------------------------------------------------
    // CONNECT TO PEER
    // ---------------------------------------------------------

    fun connectToPeer(
        device: WifiP2pDevice
    ) {

        if (!hasRequiredPermissions()) {
            Log.e(
                TAG,
                "Cannot connect: missing permissions"
            )
            return
        }

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }

        try {
            manager.connect(
                channel,
                config,
                object : WifiP2pManager.ActionListener {

                    override fun onSuccess() {
                        Log.d(
                            TAG,
                            "Connection initiated: ${device.deviceName}"
                        )
                    }

                    override fun onFailure(reason: Int) {
                        Log.e(
                            TAG,
                            "Connection failed: $reason"
                        )
                    }
                }
            )
        } catch (e: SecurityException) {
            Log.e(
                TAG,
                "connect denied",
                e
            )
        }
    }

    // ---------------------------------------------------------
    // STOP DISCOVERY
    // ---------------------------------------------------------

    fun stopDiscovery() {

        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {
            // Receiver may already be unregistered.
        }

        discoveryStatus =
            PeerDiscoveryStatus.STOPPED

        Log.d(
            TAG,
            "Discovery stopped"
        )
    }
}