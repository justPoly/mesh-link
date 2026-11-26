package com.orliczspace.mesh_link.network

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

import java.net.InetAddress

// ---------------------------------------------------------
// Peer discovery status
// ---------------------------------------------------------

enum class PeerDiscoveryStatus {
    INITIALIZING,
    DISCOVERING,
    PEERS_FOUND,
    FAILED,
    STOPPED
}

// ---------------------------------------------------------
// Wi-Fi Direct connection state
// ---------------------------------------------------------

enum class WifiDirectConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

// ---------------------------------------------------------
// Wi-Fi Direct connection information
// ---------------------------------------------------------

data class WifiDirectConnectionInfo(
    val isGroupOwner: Boolean,
    val groupOwnerAddress: InetAddress
)

// ---------------------------------------------------------
// Neighbour discovery service
// ---------------------------------------------------------

/**
 * Handles Wi-Fi Direct peer discovery, connection establishment
 * and group information.
 *
 * Public methods should be called on the main thread.
 */
class NeighbourDiscoveryService(
    private val context: Context
) {

    companion object {

        private const val TAG = "NeighbourDiscovery"

        private const val CONNECTION_TIMEOUT_MS =
            60_000L
    }

    // ---------------------------------------------------------
    // Persistent node identity
    // ---------------------------------------------------------

    val localNodeId: String by lazy {

        NodeIdentity.getOrCreate(
            context.applicationContext
        )
    }

    // ---------------------------------------------------------
    // Observable discovery state
    // ---------------------------------------------------------

    var discoveryStatus by mutableStateOf(
        PeerDiscoveryStatus.INITIALIZING
    )
        private set

    var connectionState by mutableStateOf(
        WifiDirectConnectionState.DISCONNECTED
    )
        private set

    var connectionError by mutableStateOf<String?>(null)
        private set

    var currentConnectionInfo by mutableStateOf<
            WifiDirectConnectionInfo?>(null)
        private set

    // ---------------------------------------------------------
    // Discovered peers
    // ---------------------------------------------------------

    /**
     * Device names retained for compatibility with
     * existing Mesh-Link screens.
     */
    val discoveredPeers =
        mutableStateListOf<String>()

    /**
     * Actual Wi-Fi Direct devices used for connection
     * establishment.
     */
    val discoveredDevices =
        mutableStateListOf<WifiP2pDevice>()

    /**
     * Wi-Fi Direct group information.
     *
     * Key: GO or CLIENT
     * Value: Group owner IP address
     *
     * This is not a complete list of mesh endpoints.
     */
    val connectedPeers =
        mutableStateMapOf<String, String>()

    // ---------------------------------------------------------
    // Connection callbacks
    // ---------------------------------------------------------

    var onWifiDirectConnected:
            ((WifiDirectConnectionInfo) -> Unit)? = null

    var onWifiDirectDisconnected:
            (() -> Unit)? = null

    // ---------------------------------------------------------
    // Wi-Fi Direct manager
    // ---------------------------------------------------------

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

    private val handler =
        Handler(Looper.getMainLooper())

    private var receiverRegistered = false

    private var active = false

    private var connectionAttempt = 0L

    // ---------------------------------------------------------
    // Connection negotiation timeout
    // ---------------------------------------------------------

    private val connectionTimeout = Runnable {

        if (
            active &&
            connectionState ==
            WifiDirectConnectionState.CONNECTING
        ) {

            connectionAttempt++

            connectionState =
                WifiDirectConnectionState.FAILED

            connectionError =
                "Connection timed out. Please try again."

            if (hasRequiredPermissions()) {

                try {

                    manager.cancelConnect(
                        channel,
                        null
                    )

                } catch (e: SecurityException) {

                    Log.e(
                        TAG,
                        "cancelConnect permission denied",
                        e
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Unable to cancel connection",
                        e
                    )
                }
            }

            Log.w(
                TAG,
                "WIFI_DIRECT_CONNECTION_TIMEOUT"
            )
        }
    }

    // ---------------------------------------------------------
    // Wi-Fi Direct peer list listener
    // ---------------------------------------------------------

    private val peerListListener =
        WifiP2pManager.PeerListListener { peers ->

            if (active) {

                val devices =
                    peers.deviceList.toList()

                discoveredDevices.clear()

                discoveredDevices.addAll(devices)

                discoveredPeers.clear()

                discoveredPeers.addAll(
                    devices.map { device ->

                        device.deviceName
                            ?: "Unknown Device"
                    }
                )

                discoveryStatus =
                    if (devices.isEmpty()) {

                        PeerDiscoveryStatus.DISCOVERING

                    } else {

                        PeerDiscoveryStatus.PEERS_FOUND
                    }

                Log.d(
                    TAG,
                    "Wi-Fi Direct devices found: " +
                            devices.size
                )

                devices.forEach { device ->

                    Log.d(
                        TAG,
                        "WIFI_DIRECT_PEER: " +
                                "${device.deviceName} | " +
                                device.deviceAddress
                    )
                }
            }
        }

    // ---------------------------------------------------------
    // Wi-Fi Direct connection information listener
    // ---------------------------------------------------------

    private val connectionInfoListener =
        WifiP2pManager.ConnectionInfoListener { info ->

            if (!active) {

                return@ConnectionInfoListener
            }

            if (!info.groupFormed) {

                // An empty group query is normal while
                // connection negotiation is in progress.

                if (
                    connectionState !=
                    WifiDirectConnectionState.CONNECTING &&
                    connectionState !=
                    WifiDirectConnectionState.FAILED
                ) {

                    handleWifiDirectDisconnected()
                }

                return@ConnectionInfoListener
            }

            val ownerAddress =
                info.groupOwnerAddress
                    ?: return@ConnectionInfoListener

            val connection =
                WifiDirectConnectionInfo(

                    isGroupOwner =
                        info.isGroupOwner,

                    groupOwnerAddress =
                        ownerAddress
                )

            val previousConnection =
                currentConnectionInfo

            handler.removeCallbacks(
                connectionTimeout
            )

            currentConnectionInfo =
                connection

            connectionState =
                WifiDirectConnectionState.CONNECTED

            connectionError = null

            val role =
                if (info.isGroupOwner) {

                    "GO"

                } else {

                    "CLIENT"
                }

            val groupOwnerIp =
                ownerAddress.hostAddress.orEmpty()

            connectedPeers.clear()

            connectedPeers[role] =
                groupOwnerIp

            Log.d(
                TAG,
                "WIFI_DIRECT_CONNECTED: " +
                        "role=$role | " +
                        "owner=$groupOwnerIp"
            )

            if (previousConnection != connection) {

                NetworkActivityLog.record(

                    message =
                        "Wi-Fi Direct connected",

                    detail =
                        "$role @ $groupOwnerIp"
                )

                onWifiDirectConnected?.invoke(
                    connection
                )
            }
        }

    // ---------------------------------------------------------
    // Handle Wi-Fi Direct disconnection
    // ---------------------------------------------------------

    private fun handleWifiDirectDisconnected() {

        val wasConnected =
            currentConnectionInfo != null

        connectionAttempt++

        handler.removeCallbacks(
            connectionTimeout
        )

        currentConnectionInfo = null

        connectedPeers.clear()

        connectionState =
            WifiDirectConnectionState.DISCONNECTED

        connectionError = null

        if (wasConnected) {

            NetworkActivityLog.record(

                message =
                    "Wi-Fi Direct disconnected",

                detail =
                    "Local P2P group unavailable"
            )

            Log.w(
                TAG,
                "WIFI_DIRECT_DISCONNECTED"
            )

            onWifiDirectDisconnected?.invoke()
        }
    }

    // ---------------------------------------------------------
    // Broadcast receiver
    // ---------------------------------------------------------

    private val receiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

                if (!active) return

                when (intent?.action) {

                    // -----------------------------------------
                    // Wi-Fi Direct enabled or disabled
                    // -----------------------------------------

                    WifiP2pManager
                        .WIFI_P2P_STATE_CHANGED_ACTION -> {

                        val state =
                            intent.getIntExtra(

                                WifiP2pManager
                                    .EXTRA_WIFI_STATE,

                                WifiP2pManager
                                    .WIFI_P2P_STATE_DISABLED
                            )

                        if (
                            state ==
                            WifiP2pManager
                                .WIFI_P2P_STATE_DISABLED
                        ) {

                            handleWifiDirectDisconnected()

                            discoveredDevices.clear()

                            discoveredPeers.clear()

                            discoveryStatus =
                                PeerDiscoveryStatus.FAILED

                            Log.w(
                                TAG,
                                "Wi-Fi Direct disabled"
                            )

                        } else {

                            Log.d(
                                TAG,
                                "Wi-Fi Direct enabled"
                            )
                        }
                    }

                    // -----------------------------------------
                    // Nearby peer list changed
                    // -----------------------------------------

                    WifiP2pManager
                        .WIFI_P2P_PEERS_CHANGED_ACTION -> {

                        requestPeers()
                    }

                    // -----------------------------------------
                    // Wi-Fi Direct connection changed
                    // -----------------------------------------

                    WifiP2pManager
                        .WIFI_P2P_CONNECTION_CHANGED_ACTION -> {

                        requestConnectionInfo()
                    }
                }
            }
        }

    // ---------------------------------------------------------
    // Runtime permission checks
    // ---------------------------------------------------------

    private fun hasRequiredPermissions(): Boolean {

        val fineLocation =
            ContextCompat.checkSelfPermission(

                context,

                Manifest.permission.ACCESS_FINE_LOCATION

            ) == PackageManager.PERMISSION_GRANTED

        val nearbyPermission =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
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
    // Request discovered peers
    // ---------------------------------------------------------

    private fun requestPeers() {

        if (!active) return

        if (!hasRequiredPermissions()) {

            discoveryStatus =
                PeerDiscoveryStatus.FAILED

            Log.w(
                TAG,
                "Cannot request peers: missing permissions"
            )

            return
        }

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
                "requestPeers permission denied",
                e
            )

        } catch (e: Exception) {

            discoveryStatus =
                PeerDiscoveryStatus.FAILED

            Log.e(
                TAG,
                "Unable to request peers",
                e
            )
        }
    }

    // ---------------------------------------------------------
    // Request Wi-Fi Direct connection information
    // ---------------------------------------------------------

    private fun requestConnectionInfo() {

        if (!active) return

        if (!hasRequiredPermissions()) {

            Log.w(
                TAG,
                "Cannot request connection information: " +
                        "missing permissions"
            )

            return
        }

        try {

            manager.requestConnectionInfo(
                channel,
                connectionInfoListener
            )

        } catch (e: SecurityException) {

            Log.e(
                TAG,
                "requestConnectionInfo permission denied",
                e
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Unable to request connection information",
                e
            )
        }
    }

    // ---------------------------------------------------------
    // Start Wi-Fi Direct discovery
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

        active = true

        Log.d(
            TAG,
            "Local Mesh-Link Node ID: $localNodeId"
        )

        // -----------------------------------------------------
        // Register broadcast receiver only once
        // -----------------------------------------------------

        if (!receiverRegistered) {

            val filter = IntentFilter().apply {

                addAction(
                    WifiP2pManager
                        .WIFI_P2P_STATE_CHANGED_ACTION
                )

                addAction(
                    WifiP2pManager
                        .WIFI_P2P_PEERS_CHANGED_ACTION
                )

                addAction(
                    WifiP2pManager
                        .WIFI_P2P_CONNECTION_CHANGED_ACTION
                )
            }

            try {

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
                ) {

                    context.registerReceiver(

                        receiver,

                        filter,

                        Context.RECEIVER_EXPORTED
                    )

                } else {

                    context.registerReceiver(
                        receiver,
                        filter
                    )
                }

                receiverRegistered = true

            } catch (e: SecurityException) {

                active = false

                discoveryStatus =
                    PeerDiscoveryStatus.FAILED

                Log.e(
                    TAG,
                    "Receiver registration permission denied",
                    e
                )

                return

            } catch (e: Exception) {

                active = false

                discoveryStatus =
                    PeerDiscoveryStatus.FAILED

                Log.e(
                    TAG,
                    "Receiver registration failed",
                    e
                )

                return
            }
        }

        // Check whether an existing group is available.

        requestConnectionInfo()

        // Do not interrupt an ongoing connection attempt.

        if (
            connectionState ==
            WifiDirectConnectionState.CONNECTING
        ) {

            return
        }

        discoveryStatus =
            PeerDiscoveryStatus.DISCOVERING

        // -----------------------------------------------------
        // Begin Wi-Fi Direct discovery
        // -----------------------------------------------------

        try {

            manager.discoverPeers(

                channel,

                object : WifiP2pManager.ActionListener {

                    override fun onSuccess() {

                        if (!active) return

                        Log.d(
                            TAG,
                            "Wi-Fi Direct discovery started"
                        )

                        requestPeers()
                    }

                    override fun onFailure(reason: Int) {

                        if (!active) return

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
                "discoverPeers permission denied",
                e
            )

        } catch (e: Exception) {

            discoveryStatus =
                PeerDiscoveryStatus.FAILED

            Log.e(
                TAG,
                "Unable to start discovery",
                e
            )
        }
    }

    // ---------------------------------------------------------
    // Connect to a discovered Wi-Fi Direct peer
    // ---------------------------------------------------------

    fun connectToPeer(
        device: WifiP2pDevice
    ) {

        if (!active || !hasRequiredPermissions()) {

            connectionError =
                "Start discovery and grant " +
                        "Wi-Fi Direct permissions first."

            connectionState =
                WifiDirectConnectionState.FAILED

            return
        }

        // Prevent duplicate connection attempts.

        if (
            connectionState ==
            WifiDirectConnectionState.CONNECTING ||
            connectionState ==
            WifiDirectConnectionState.CONNECTED
        ) {

            return
        }

        // Validate device address.

        if (device.deviceAddress.isNullOrBlank()) {

            connectionError =
                "Device address unavailable."

            connectionState =
                WifiDirectConnectionState.FAILED

            return
        }

        connectionState =
            WifiDirectConnectionState.CONNECTING

        connectionError = null

        val attempt =
            ++connectionAttempt

        val config = WifiP2pConfig().apply {

            deviceAddress =
                device.deviceAddress
        }

        // Start connection negotiation timeout.

        handler.removeCallbacks(
            connectionTimeout
        )

        handler.postDelayed(

            connectionTimeout,

            CONNECTION_TIMEOUT_MS
        )

        // -----------------------------------------------------
        // Initiate Wi-Fi Direct connection
        // -----------------------------------------------------

        try {

            manager.connect(

                channel,

                config,

                object : WifiP2pManager.ActionListener {

                    override fun onSuccess() {

                        if (
                            !active ||
                            attempt != connectionAttempt
                        ) {

                            return
                        }

                        Log.d(
                            TAG,
                            "WIFI_DIRECT_CONNECTION_REQUESTED: " +
                                    device.deviceName
                        )

                        // A successful request does not mean
                        // that the group has formed yet.

                        requestConnectionInfo()
                    }

                    override fun onFailure(reason: Int) {

                        if (
                            !active ||
                            attempt != connectionAttempt ||
                            connectionState !=
                            WifiDirectConnectionState.CONNECTING
                        ) {

                            return
                        }

                        handler.removeCallbacks(
                            connectionTimeout
                        )

                        connectionState =
                            WifiDirectConnectionState.FAILED

                        connectionError =
                            "Connection failed: $reason"

                        Log.e(
                            TAG,
                            "Connection failed: $reason"
                        )
                    }
                }
            )

        } catch (e: SecurityException) {

            handler.removeCallbacks(
                connectionTimeout
            )

            connectionState =
                WifiDirectConnectionState.FAILED

            connectionError =
                "Wi-Fi Direct permission denied"

            Log.e(
                TAG,
                "connect permission denied",
                e
            )

        } catch (e: Exception) {

            handler.removeCallbacks(
                connectionTimeout
            )

            connectionState =
                WifiDirectConnectionState.FAILED

            connectionError =
                e.message ?: "Connection failed"

            Log.e(
                TAG,
                "connect failed",
                e
            )
        }
    }

    // ---------------------------------------------------------
    // Stop Wi-Fi Direct discovery
    // ---------------------------------------------------------

    fun stopDiscovery() {

        active = false

        handler.removeCallbacks(
            connectionTimeout
        )

        if (hasRequiredPermissions()) {

            // -------------------------------------------------
            // Stop peer discovery
            // -------------------------------------------------

            try {

                manager.stopPeerDiscovery(
                    channel,
                    null
                )

            } catch (e: SecurityException) {

                Log.e(
                    TAG,
                    "stopPeerDiscovery permission denied",
                    e
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Unable to stop peer discovery",
                    e
                )
            }

            // -------------------------------------------------
            // Cancel pending connection negotiation
            // -------------------------------------------------

            if (
                connectionState ==
                WifiDirectConnectionState.CONNECTING
            ) {

                try {

                    manager.cancelConnect(
                        channel,
                        null
                    )

                } catch (e: SecurityException) {

                    Log.e(
                        TAG,
                        "cancelConnect permission denied",
                        e
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Unable to cancel connection",
                        e
                    )
                }
            }
        }

        // -----------------------------------------------------
        // Unregister broadcast receiver
        // -----------------------------------------------------

        if (receiverRegistered) {

            try {

                context.unregisterReceiver(
                    receiver
                )

            } catch (e: IllegalArgumentException) {

                Log.w(
                    TAG,
                    "Receiver already unregistered",
                    e
                )

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Receiver cleanup failed",
                    e
                )
            }

            receiverRegistered = false
        }

        // -----------------------------------------------------
        // Clear discovery state
        // -----------------------------------------------------

        discoveredDevices.clear()

        discoveredPeers.clear()

        handleWifiDirectDisconnected()

        discoveryStatus =
            PeerDiscoveryStatus.STOPPED

        Log.d(
            TAG,
            "Wi-Fi Direct discovery stopped"
        )
    }
}