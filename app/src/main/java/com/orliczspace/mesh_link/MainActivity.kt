package com.orliczspace.mesh_link

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.orliczspace.mesh_link.ui.screen.permission.PermissionScreen
import com.orliczspace.mesh_link.network.*
import com.orliczspace.mesh_link.network.gateway.GatewayNatService
import com.orliczspace.mesh_link.network.gateway.SQLiteFlowLogger
import com.orliczspace.mesh_link.network.gateway.NatEntry
import com.orliczspace.mesh_link.network.vpn.MeshVpnService
import com.orliczspace.mesh_link.ui.legacy.MeshlinkTheme
import com.orliczspace.mesh_link.ui.navigation.MeshNavGraph
import kotlinx.coroutines.delay
import java.net.DatagramSocket

class MainActivity : ComponentActivity() {

    companion object {
        private const val VPN_REQUEST_CODE = 1001
        private const val TAG = "MainActivity"
    }

    /* ---------- Core runtime services ---------- */

    private var meshVpnService: MeshVpnService? = null
    private lateinit var packetForwarder: PacketForwarder
    private lateinit var meshNetworkManager: MeshNetworkManager

    private lateinit var linkProbeService: LinkProbeService
    private lateinit var routingRepository: RoutingStateRepository
    private lateinit var gatewayNatService: GatewayNatService
    private lateinit var internetMonitor: InternetMonitor
    private lateinit var adaptiveProbeScheduler: AdaptiveProbeScheduler
    private var neighbourService: NeighbourDiscoveryService? = null

    /* ---------- VPN SERVICE CONNECTION ---------- */

    private val vpnConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as MeshVpnService.LocalBinder).getService()
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

        /* ---------- Core initialization (CORRECT ORDER) ---------- */

        val localNodeId = Build.MODEL ?: "unknown-node"

        linkProbeService = LinkProbeService(localNodeId).apply { start() }
        adaptiveProbeScheduler = AdaptiveProbeScheduler(linkProbeService)
        routingRepository = RoutingStateRepository(linkProbeService)
        internetMonitor = InternetMonitor(this)

        val flowLogger = SQLiteFlowLogger(this)

        gatewayNatService = GatewayNatService(
            flowLogger = flowLogger,
            onInboundPacket = { payload ->
                meshVpnService?.writeToTun(payload)
            }
        )

        val socket = DatagramSocket()

        // 1️⃣ Create PacketForwarder FIRST
        packetForwarder = PacketForwarder(
            socket = socket,
            routingRepository = routingRepository,
            gatewayNatService = gatewayNatService
        )

        // 2️⃣ Create MeshNetworkManager
        meshNetworkManager = MeshNetworkManager(
            localNodeId = localNodeId,
            socket = socket,
            packetForwarder = packetForwarder
        )

        // 3️⃣ Inject MeshNetworkManager into PacketForwarder
        packetForwarder.meshNetworkManager = meshNetworkManager

        /* ---------- UI ---------- */

        setContent {
            var hasPermissions by remember { mutableStateOf(checkRequiredPermissions()) }

            val permissionLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    hasPermissions = checkRequiredPermissions()
                }

            LaunchedEffect(Unit) {
                if (!hasPermissions) {
                    permissionLauncher.launch(getRequiredPermissions())
                }
            }

            LaunchedEffect(hasPermissions) {
                if (hasPermissions) {
                    startMeshVpn()
                    startNeighbourDiscovery()
                }
            }

            MeshlinkTheme {

                if (hasPermissions) {

                    MeshNavGraph()

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

    /* ---------- Networking ---------- */

    private fun startNeighbourDiscovery() {
        neighbourService = NeighbourDiscoveryService(this).apply {
            startDiscovery()
        }
    }

    private fun startMeshVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) startActivityForResult(intent, VPN_REQUEST_CODE)
        else startVpnService()
    }

    private fun startVpnService() {
        val intent = Intent(this, MeshVpnService::class.java)
        startService(intent)
        bindService(intent, vpnConnection, BIND_AUTO_CREATE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            startVpnService()
        }
    }

    /* ---------- Permissions ---------- */

    private fun checkRequiredPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val nearby =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) == PackageManager.PERMISSION_GRANTED
            } else true

        return fine && coarse && nearby
    }

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        return permissions.toTypedArray()
    }

    override fun onDestroy() {
        super.onDestroy()
        neighbourService?.stopDiscovery()
        adaptiveProbeScheduler.stopAll()
        linkProbeService.stop()
        internetMonitor.close()
        unbindService(vpnConnection)
    }
}

/* ---------------- UI COMPOSABLES ---------------- */



