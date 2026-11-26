package com.orliczspace.mesh_link

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

import com.orliczspace.mesh_link.network.NodeIdentity
import com.orliczspace.mesh_link.network.AdaptiveProbeScheduler
import com.orliczspace.mesh_link.network.InternetMonitor
import com.orliczspace.mesh_link.network.LinkProbeService
import com.orliczspace.mesh_link.network.MeshNetworkManager
import com.orliczspace.mesh_link.network.MeshReachabilityService
import com.orliczspace.mesh_link.network.MeshReachabilityState
import com.orliczspace.mesh_link.network.NeighbourDiscoveryService
import com.orliczspace.mesh_link.network.PacketForwarder
import com.orliczspace.mesh_link.network.PeerConnectionState
import com.orliczspace.mesh_link.network.PeerRegistry
import com.orliczspace.mesh_link.network.RoutingStateRepository

import com.orliczspace.mesh_link.network.gateway.GatewayNatService
import com.orliczspace.mesh_link.network.gateway.SQLiteFlowLogger
import com.orliczspace.mesh_link.network.vpn.MeshVpnService

import com.orliczspace.mesh_link.ui.legacy.MeshlinkTheme
import com.orliczspace.mesh_link.ui.navigation.MeshNavGraph
import com.orliczspace.mesh_link.ui.screen.PermissionScreen

import java.net.DatagramSocket
import java.net.InetAddress

class MainActivity : ComponentActivity() {

    companion object {

        private const val TAG = "MainActivity"

        private const val DISCOVERY_PORT = 8888

        private const val MESH_PORT = 9999

        private const val MESH_TEST_RETRY_INTERVAL_MS =
            15_000L

        // Keep VPN disabled while verifying Cards 14–21.
        // Local mesh communication does not require a VPN.

        private const val ENABLE_VPN_DURING_TESTING =
            false
    }

    // ---------------------------------------------------------
    // Networking components
    // ---------------------------------------------------------

    private var meshVpnService: MeshVpnService? = null

    private lateinit var localNodeId: String

    private lateinit var packetForwarder: PacketForwarder

    private lateinit var meshNetworkManager:
            MeshNetworkManager

    private lateinit var meshReachabilityService:
            MeshReachabilityService

    private lateinit var linkProbeService:
            LinkProbeService

    private lateinit var routingRepository:
            RoutingStateRepository

    private lateinit var gatewayNatService:
            GatewayNatService

    private lateinit var internetMonitor:
            InternetMonitor

    private lateinit var adaptiveProbeScheduler:
            AdaptiveProbeScheduler

    private lateinit var neighbourService:
            NeighbourDiscoveryService

    private lateinit var peerRegistry:
            PeerRegistry

    // ---------------------------------------------------------
    // Lifecycle state
    // ---------------------------------------------------------

    @Volatile
    private var isNetworkingShuttingDown = false

    private var isVpnBound = false

    private var networkingStarted = false

    private val lastMeshTestAttempts =
        mutableMapOf<String, Long>()

    // ---------------------------------------------------------
    // VPN service connection
    // ---------------------------------------------------------

    private val vpnConnection =
        object : ServiceConnection {

            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?
            ) {

                if (isNetworkingShuttingDown) return

                val localBinder =
                    binder as? MeshVpnService.LocalBinder

                if (localBinder == null) {

                    Log.e(
                        TAG,
                        "Invalid VPN service binder"
                    )

                    return
                }

                val service =
                    localBinder.getService()

                meshVpnService = service

                if (::packetForwarder.isInitialized) {

                    service.attachPacketForwarder(
                        packetForwarder
                    )

                    Log.d(
                        TAG,
                        "PacketForwarder attached to VPN service"
                    )
                }
            }

            override fun onServiceDisconnected(
                name: ComponentName?
            ) {

                meshVpnService = null

                Log.w(
                    TAG,
                    "VPN service disconnected"
                )
            }
        }

    // ---------------------------------------------------------
    // Activity lifecycle
    // ---------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen()

        super.onCreate(savedInstanceState)

        // Stop an older Mesh-Link VPN instance during
        // the local networking diagnostic phase.

        if (!ENABLE_VPN_DURING_TESTING) {

            stopService(
                Intent(
                    this,
                    MeshVpnService::class.java
                )
            )
        }

        initializeNetworking()

        setContent {

            var hasPermissions by remember {

                mutableStateOf(
                    checkRequiredPermissions()
                )
            }

            var vpnStarted by remember {

                mutableStateOf(false)
            }

            val permissionLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts
                        .RequestMultiplePermissions()
                ) {

                    hasPermissions =
                        checkRequiredPermissions()
                }

            val vpnLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts
                        .StartActivityForResult()
                ) { result ->

                    if (result.resultCode == RESULT_OK) {

                        startVpnService()

                    } else {

                        vpnStarted = false

                        Log.w(
                            TAG,
                            "VPN permission was not granted"
                        )
                    }
                }

            LaunchedEffect(Unit) {

                if (!hasPermissions) {

                    permissionLauncher.launch(
                        getRequiredPermissions()
                    )
                }
            }

            LaunchedEffect(hasPermissions) {

                if (hasPermissions) {

                    startNetworkingServices()

                    if (
                        ENABLE_VPN_DURING_TESTING &&
                        !vpnStarted
                    ) {

                        vpnStarted = true

                        startMeshVpn(vpnLauncher)
                    }
                }
            }

            MeshlinkTheme {

                if (hasPermissions) {


                    MeshNavGraph(

                        linkProbeService =
                            linkProbeService,

                        routingRepository =
                            routingRepository,

                        internetMonitor =
                            internetMonitor,

                        neighbourService =
                            neighbourService,

                        gatewayNatService =
                            gatewayNatService,

                        localNodeId =
                            localNodeId,

                        peerRegistry =
                            peerRegistry,

                        packetForwarder =
                            packetForwarder,

                        meshReachabilityService =
                            meshReachabilityService
                    )


                } else {

                    PermissionScreen {

                        permissionLauncher.launch(
                            getRequiredPermissions()
                        )
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------
    // Initialize networking components
    // ---------------------------------------------------------

    private fun initializeNetworking() {

        isNetworkingShuttingDown = false

        // -----------------------------------------------------
        // 1. Persistent node identity
        // -----------------------------------------------------

        localNodeId =
            NodeIdentity.getOrCreate(
                applicationContext
            )

        Log.d(
            TAG,
            "Persistent Mesh-link Node ID: $localNodeId"
        )

        Log.d(
            TAG,
            "Local device: " +
                    "${Build.MANUFACTURER} ${Build.MODEL}"
        )

        // -----------------------------------------------------
        // 2. Internet monitoring
        // -----------------------------------------------------

        internetMonitor =
            InternetMonitor(this)

        // -----------------------------------------------------
        // 3. Central peer registry
        // -----------------------------------------------------

        peerRegistry =
            PeerRegistry()

        // -----------------------------------------------------
        // 4. Link discovery and heartbeat
        // -----------------------------------------------------

        linkProbeService =
            LinkProbeService(
                localNodeId = localNodeId,

                listenPort = DISCOVERY_PORT,

                peerRegistry = peerRegistry,

                internetCapabilityProvider = {

                    internetMonitor
                        .hasDirectInternetAccess()
                },

                meshPort = MESH_PORT
            )

        // -----------------------------------------------------
        // 5. Adaptive probe scheduler
        // -----------------------------------------------------

        adaptiveProbeScheduler =
            AdaptiveProbeScheduler(
                linkProbeService
            )

        // -----------------------------------------------------
        // 6. Routing repository
        // -----------------------------------------------------

        routingRepository =
            RoutingStateRepository(
                linkProbeService
            )

        // -----------------------------------------------------
        // 7. Wi-Fi Direct discovery
        // -----------------------------------------------------

        neighbourService =
            NeighbourDiscoveryService(this)

        // -----------------------------------------------------
        // 8. Gateway NAT service
        // -----------------------------------------------------

        gatewayNatService =
            GatewayNatService(

                flowLogger =
                    SQLiteFlowLogger(this),

                onInboundPacket = { packet ->

                    if (ENABLE_VPN_DURING_TESTING) {

                        meshVpnService
                            ?.writeToTun(packet)
                    }
                }
            )

        // -----------------------------------------------------
        // 9. Shared mesh UDP socket
        // -----------------------------------------------------

        val socket =
            DatagramSocket(MESH_PORT)

        // -----------------------------------------------------
        // 10. Packet forwarder
        // -----------------------------------------------------

        packetForwarder =
            PacketForwarder(

                socket = socket,

                routingRepository =
                    routingRepository,

                gatewayNatService =
                    gatewayNatService
            )

        // -----------------------------------------------------
        // 11. Mesh network manager
        // -----------------------------------------------------

        meshNetworkManager =
            MeshNetworkManager(

                localNodeId =
                    localNodeId,

                socket =
                    socket,

                packetForwarder =
                    packetForwarder
            )

        packetForwarder.meshNetworkManager =
            meshNetworkManager

        packetForwarder.localNodeId =
            localNodeId

        // -----------------------------------------------------
        // 12. Mesh transport reachability service
        // -----------------------------------------------------

        meshReachabilityService =
            MeshReachabilityService(

                localNodeId =
                    localNodeId,

                peerRegistry =
                    peerRegistry,

                packetForwarder =
                    packetForwarder
            )

        meshNetworkManager.meshReachabilityService =
            meshReachabilityService

        // -----------------------------------------------------
        // 13. Configure networking callbacks
        // -----------------------------------------------------

        configureNetworkingCallbacks()

        Log.d(
            TAG,
            "Mesh-link networking initialized successfully"
        )
    }

    // ---------------------------------------------------------
    // Start networking services
    // ---------------------------------------------------------

    private fun startNetworkingServices() {

        if (
            networkingStarted ||
            isNetworkingShuttingDown
        ) {
            return
        }

        packetForwarder.startReceiver()

        linkProbeService.start()

        networkingStarted = true

        startNeighbourDiscovery()

        Log.d(
            TAG,
            "NETWORKING_STARTED: " +
                    "discoveryPort=$DISCOVERY_PORT | " +
                    "meshPort=$MESH_PORT | " +
                    "vpnEnabled=$ENABLE_VPN_DURING_TESTING"
        )
    }

    // ---------------------------------------------------------
    // Configure networking callbacks
    // ---------------------------------------------------------

    private fun configureNetworkingCallbacks() {

        neighbourService.onWifiDirectConnected = { info ->
            if (!isNetworkingShuttingDown) {
                Log.d(TAG, "WIFI_DIRECT_NETWORK_READY: owner=${info.groupOwnerAddress.hostAddress} | isGroupOwner=${info.isGroupOwner}")
                if (!info.isGroupOwner) {
                    linkProbeService.sendProbe(info.groupOwnerAddress, DISCOVERY_PORT)
                }
                linkProbeService.requestDiscovery()
            }
        }

        neighbourService.onWifiDirectDisconnected = {
            if (!isNetworkingShuttingDown) {
                Log.w(TAG, "WIFI_DIRECT_DISCONNECTED: awaiting peer timeout")
            }
        }



        // -----------------------------------------------------
        // Peer discovery
        // -----------------------------------------------------

        linkProbeService.onPeerDiscovered = {
                nodeId, ipAddress ->

            if (!isNetworkingShuttingDown) {

                Log.d(
                    TAG,
                    "PEER_DISCOVERED: " +
                            "$nodeId @ $ipAddress"
                )
            }
        }

        // -----------------------------------------------------
        // Peer connection established
        // -----------------------------------------------------

        linkProbeService.onPeerConnected = { peer ->

            if (!isNetworkingShuttingDown) {

                Log.d(
                    TAG,
                    "PEER_CONNECTED: ${peer.nodeId} | " +
                            "Device=${peer.deviceName} | " +
                            "IP=${peer.ipAddress} | " +
                            "Port=${peer.meshPort} | " +
                            "Internet=${peer.hasInternetAccess}"
                )
            }
        }

        // -----------------------------------------------------
        // Successful discovery or heartbeat response
        // -----------------------------------------------------

        linkProbeService.onProbeResponse = { nodeId ->

            runOnUiThread {

                if (!isNetworkingShuttingDown) {

                    val peer =
                        peerRegistry.getPeer(nodeId)

                    if (
                        peer != null &&
                        peer.connectionState ==
                        PeerConnectionState.CONNECTED
                    ) {

                        try {

                            val address =
                                InetAddress.getByName(
                                    peer.ipAddress
                                )

                            val previousEndpoint =
                                packetForwarder
                                    .getRegisteredPeerAddresses()[
                                    nodeId
                                ]

                            val endpointChanged =
                                previousEndpoint != null &&
                                        (
                                                previousEndpoint.address !=
                                                        address ||
                                                        previousEndpoint.port !=
                                                        peer.meshPort
                                                )

                            if (endpointChanged) {

                                meshReachabilityService
                                    .cancelPeerTests(nodeId)

                                meshNetworkManager
                                    .removePeerSession(nodeId)

                                lastMeshTestAttempts.remove(
                                    nodeId
                                )

                                peerRegistry.updatePeer(
                                    nodeId
                                ) { current ->

                                    current.copy(

                                        meshReachability =
                                            MeshReachabilityState
                                                .NOT_TESTED,

                                        meshLatencyMs = null
                                    )
                                }
                            }

                            // Register actual forwarding endpoint.

                            packetForwarder.registerPeerAddress(
                                nodeId = peer.nodeId,
                                address = address,
                                port = peer.meshPort
                            )

                            // Notify adaptive probe scheduler.

                            adaptiveProbeScheduler.notifyResponse(
                                nodeId
                            )

                            // Update routing repository.

                            routingRepository.updateNode(

                                nodeId =
                                    nodeId,

                                hasInternetAccess =
                                    peer.hasInternetAccess
                            )

                            // Verify the actual mesh endpoint.

                            maybeTestMeshPeer(nodeId)

                            Log.d(
                                TAG,
                                "ROUTE_UPDATED: $nodeId | " +
                                        "Device=${peer.deviceName} | " +
                                        "IP=${peer.ipAddress} | " +
                                        "Port=${peer.meshPort} | " +
                                        "Internet=${peer.hasInternetAccess} | " +
                                        "RTT=${peer.latencyMs}ms"
                            )

                        } catch (e: Exception) {

                            Log.e(
                                TAG,
                                "Failed to update peer route: $nodeId",
                                e
                            )
                        }
                    }
                }
            }
        }

        // -----------------------------------------------------
        // Peer timeout
        // -----------------------------------------------------

        linkProbeService.onPeerLost = { nodeId ->

            runOnUiThread {

                if (!isNetworkingShuttingDown) {

                    val peer =
                        peerRegistry.getPeer(nodeId)

                    if (
                        peer?.connectionState !=
                        PeerConnectionState.CONNECTED
                    ) {

                        adaptiveProbeScheduler.stopProbing(
                            nodeId
                        )

                        meshReachabilityService.cancelPeerTests(
                            nodeId
                        )

                        meshNetworkManager.removePeerSession(
                            nodeId
                        )

                        packetForwarder.removePeerAddress(
                            nodeId
                        )

                        routingRepository.removeNode(
                            nodeId
                        )

                        lastMeshTestAttempts.remove(
                            nodeId
                        )

                        Log.w(
                            TAG,
                            "PEER_LOST: $nodeId | " +
                                    "Endpoint, session and route removed"
                        )
                    }
                }
            }
        }

        // -----------------------------------------------------
        // Peer reconnection
        // -----------------------------------------------------

        linkProbeService.onPeerReconnected = { peer ->

            if (!isNetworkingShuttingDown) {

                Log.d(
                    TAG,
                    "PEER_RECONNECTED: ${peer.nodeId} | " +
                            "Device=${peer.deviceName} | " +
                            "IP=${peer.ipAddress} | " +
                            "Internet=${peer.hasInternetAccess}"
                )

                // The next successful probe response
                // restores the endpoint, route and mesh test.
            }
        }
    }

    // ---------------------------------------------------------
    // Automatically test mesh transport
    // ---------------------------------------------------------

    private fun maybeTestMeshPeer(nodeId: String) {

        if (isNetworkingShuttingDown) return

        val peer =
            peerRegistry.getPeer(nodeId)
                ?: return

        if (
            peer.connectionState !=
            PeerConnectionState.CONNECTED
        ) {
            return
        }

        val now =
            SystemClock.elapsedRealtime()

        val shouldTest =
            when (peer.meshReachability) {

                MeshReachabilityState.NOT_TESTED -> {

                    true
                }

                MeshReachabilityState.UNREACHABLE -> {

                    val lastAttempt =
                        lastMeshTestAttempts[nodeId]

                    lastAttempt == null ||
                            now - lastAttempt >=
                            MESH_TEST_RETRY_INTERVAL_MS
                }

                MeshReachabilityState.TESTING -> {

                    false
                }

                MeshReachabilityState.REACHABLE -> {

                    false
                }
            }

        if (!shouldTest) return

        val started =
            meshReachabilityService.testPeer(
                nodeId
            )

        if (started) {

            lastMeshTestAttempts[nodeId] = now

            Log.d(
                TAG,
                "Automatic mesh test started for $nodeId"
            )
        }
    }

    // ---------------------------------------------------------
    // Wi-Fi Direct neighbour discovery
    // ---------------------------------------------------------

    private fun startNeighbourDiscovery() {

        if (
            !isNetworkingShuttingDown &&
            ::neighbourService.isInitialized
        ) {

            neighbourService.startDiscovery()

            Log.d(
                TAG,
                "Wi-Fi Direct discovery started"
            )
        }
    }

    // ---------------------------------------------------------
    // VPN permission
    // ---------------------------------------------------------

    private fun startMeshVpn(
        launcher: ActivityResultLauncher<Intent>
    ) {

        if (!ENABLE_VPN_DURING_TESTING) return

        val intent =
            VpnService.prepare(this)

        if (intent != null) {

            launcher.launch(intent)

        } else {

            startVpnService()
        }
    }

    // ---------------------------------------------------------
    // Start and bind VPN service
    // ---------------------------------------------------------

    private fun startVpnService() {

        if (
            !ENABLE_VPN_DURING_TESTING ||
            isVpnBound ||
            isNetworkingShuttingDown
        ) {
            return
        }

        val intent =
            Intent(
                this,
                MeshVpnService::class.java
            )

        ContextCompat.startForegroundService(
            this,
            intent
        )

        isVpnBound =
            bindService(
                intent,
                vpnConnection,
                BIND_AUTO_CREATE
            )

        Log.d(
            TAG,
            "VPN service binding: $isVpnBound"
        )
    }

    // ---------------------------------------------------------
    // Runtime permissions
    // ---------------------------------------------------------

    private fun checkRequiredPermissions(): Boolean {

        val fine =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarse =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val nearby =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {

                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) == PackageManager.PERMISSION_GRANTED

            } else {

                true
            }

        return fine && coarse && nearby
    }

    private fun getRequiredPermissions(): Array<String> {

        val permissions =
            mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            permissions.add(
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        }

        return permissions.toTypedArray()
    }

    // ---------------------------------------------------------
    // Activity and networking cleanup
    // ---------------------------------------------------------

    override fun onDestroy() {

        isNetworkingShuttingDown = true

        networkingStarted = false

        Log.d(
            TAG,
            "Shutting down Mesh-link networking"
        )

        // Stop Wi-Fi Direct discovery.

        if (::neighbourService.isInitialized) {

            neighbourService.onWifiDirectConnected = null
            neighbourService.onWifiDirectDisconnected = null
            neighbourService.stopDiscovery()
        }

        // Stop adaptive probing.

        if (::adaptiveProbeScheduler.isInitialized) {

            adaptiveProbeScheduler.stopAll()
        }

        // Remove callbacks and stop discovery.

        if (::linkProbeService.isInitialized) {

            linkProbeService.onPeerDiscovered = null

            linkProbeService.onPeerConnected = null

            linkProbeService.onProbeResponse = null

            linkProbeService.onPeerLost = null

            linkProbeService.onPeerReconnected = null

            linkProbeService.stop()
        }

        // Stop mesh reachability diagnostics.

        if (::meshReachabilityService.isInitialized) {

            meshReachabilityService.stop()
        }

        // Stop mesh network manager.

        if (::meshNetworkManager.isInitialized) {

            meshNetworkManager.meshReachabilityService =
                null

            meshNetworkManager.stop()
        }

        // Stop internet monitoring.

        if (::internetMonitor.isInitialized) {

            internetMonitor.close()
        }

        // Unbind VPN service.

        if (isVpnBound) {

            runCatching {

                unbindService(vpnConnection)

            }.onFailure { error ->

                Log.w(
                    TAG,
                    "Failed to unbind VPN service",
                    error
                )
            }

            isVpnBound = false
        }

        meshVpnService = null

        // Stop packet forwarding and close shared socket.

        if (::packetForwarder.isInitialized) {

            packetForwarder.stop()
        }

        // Stop gateway NAT service.

        if (::gatewayNatService.isInitialized) {

            gatewayNatService.stop()
        }

        // Clear peer registry.

        if (::peerRegistry.isInitialized) {

            peerRegistry.clear()
        }

        lastMeshTestAttempts.clear()

        Log.d(
            TAG,
            "Mesh-link networking shutdown completed"
        )

        super.onDestroy()
    }
}
