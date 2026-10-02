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
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

import com.orliczspace.mesh_link.network.AdaptiveProbeScheduler
import com.orliczspace.mesh_link.network.LinkProbeService
import com.orliczspace.mesh_link.network.MeshNetworkManager
import com.orliczspace.mesh_link.network.NeighbourDiscoveryService
import com.orliczspace.mesh_link.network.PacketForwarder
import com.orliczspace.mesh_link.network.RoutingStateRepository
import com.orliczspace.mesh_link.network.InternetMonitor
import com.orliczspace.mesh_link.network.gateway.GatewayNatService
import com.orliczspace.mesh_link.network.gateway.SQLiteFlowLogger
import com.orliczspace.mesh_link.network.vpn.MeshVpnService
import com.orliczspace.mesh_link.ui.legacy.MeshlinkTheme
import com.orliczspace.mesh_link.ui.navigation.MeshNavGraph
import com.orliczspace.mesh_link.ui.screen.PermissionScreen

import java.net.DatagramSocket

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var meshVpnService: MeshVpnService? = null

    private lateinit var packetForwarder: PacketForwarder
    private lateinit var meshNetworkManager: MeshNetworkManager
    private lateinit var linkProbeService: LinkProbeService
    private lateinit var routingRepository: RoutingStateRepository
    private lateinit var gatewayNatService: GatewayNatService
    private lateinit var internetMonitor: InternetMonitor
    private lateinit var adaptiveProbeScheduler: AdaptiveProbeScheduler
    private lateinit var neighbourService: NeighbourDiscoveryService

    private val vpnConnection = object : ServiceConnection {

        override fun onServiceConnected(
            name: ComponentName?,
            binder: IBinder?
        ) {

            val service =
                (binder as MeshVpnService.LocalBinder).getService()

            meshVpnService = service

            service.attachPacketForwarder(packetForwarder)

            Log.d(TAG, "PacketForwarder attached to VPN service")
        }

        override fun onServiceDisconnected(name: ComponentName?) {

            meshVpnService = null

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen()

        super.onCreate(savedInstanceState)

        initializeNetworking()

        setContent {

            var hasPermissions by remember {
                mutableStateOf(checkRequiredPermissions())
            }

            var vpnStarted by rememberSaveable {
                mutableStateOf(false)
            }

            val permissionLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    hasPermissions = checkRequiredPermissions()
                }

            val vpnLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->

                    if (result.resultCode == RESULT_OK) {
                        startVpnService()
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

                if (hasPermissions && !vpnStarted) {

                    vpnStarted = true

                    startMeshVpn(vpnLauncher)

                    startNeighbourDiscovery()

                }

            }

            MeshlinkTheme {

                if (hasPermissions) {

                    MeshNavGraph(
                        linkProbeService = linkProbeService,
                        routingRepository = routingRepository,
                        internetMonitor = internetMonitor,
                        neighbourService = neighbourService,
                        gatewayNatService = gatewayNatService
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

    /**
     * Initialize networking layer.
     */
    private fun initializeNetworking() {

        val localNodeId =
            Build.MODEL ?: "unknown-node"

        /*
         * Link probing
         */
        linkProbeService =
            LinkProbeService(localNodeId).apply {
                start()
            }

        /*
         * Adaptive probing
         */
        adaptiveProbeScheduler =
            AdaptiveProbeScheduler(linkProbeService)

        /*
         * Routing
         */
        routingRepository =
            RoutingStateRepository(linkProbeService)

        /*
         * Internet monitoring
         */
        internetMonitor =
            InternetMonitor(this)

        /*
         * Wi-Fi Direct neighbour discovery.
         *
         * The instance is created here so the same
         * instance can be passed to Compose.
         */
        neighbourService =
            NeighbourDiscoveryService(this)

        /*
         * Gateway NAT
         */
        gatewayNatService =
            GatewayNatService(
                flowLogger = SQLiteFlowLogger(this),
                onInboundPacket = { packet ->
                    meshVpnService?.writeToTun(packet)
                }
            )

        /*
         * Shared UDP socket
         */
        val socket = DatagramSocket()

        /*
         * Packet forwarding
         */
        packetForwarder =
            PacketForwarder(
                socket = socket,
                routingRepository = routingRepository,
                gatewayNatService = gatewayNatService
            )

        /*
         * Mesh network manager
         */
        meshNetworkManager =
            MeshNetworkManager(
                localNodeId = localNodeId,
                socket = socket,
                packetForwarder = packetForwarder
            )

        packetForwarder.meshNetworkManager =
            meshNetworkManager
    }

    private fun startNeighbourDiscovery() {

        neighbourService.startDiscovery()

    }

    private fun startMeshVpn(
        launcher: androidx.activity.result.ActivityResultLauncher<Intent>
    ) {

        val intent = VpnService.prepare(this)

        if (intent != null) {

            launcher.launch(intent)

        } else {

            startVpnService()

        }
    }

    private fun startVpnService() {

        val intent =
            Intent(this, MeshVpnService::class.java)

        ContextCompat.startForegroundService(
            this,
            intent
        )

        bindService(
            intent,
            vpnConnection,
            BIND_AUTO_CREATE
        )
    }

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

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

        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            permissions.add(
                Manifest.permission.NEARBY_WIFI_DEVICES
            )

        }

        return permissions.toTypedArray()
    }

    override fun onDestroy() {

        neighbourService.stopDiscovery()

        adaptiveProbeScheduler.stopAll()

        linkProbeService.stop()

        internetMonitor.close()

        gatewayNatService.stop()

        runCatching {
            unbindService(vpnConnection)
        }

        super.onDestroy()
    }
}