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
import com.orliczspace.mesh_link.network.*
import com.orliczspace.mesh_link.network.gateway.GatewayNatService
import com.orliczspace.mesh_link.network.gateway.SQLiteFlowLogger
import com.orliczspace.mesh_link.network.gateway.NatEntry
import com.orliczspace.mesh_link.network.vpn.MeshVpnService
import com.orliczspace.mesh_link.ui.legacy.MeshlinkTheme
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

            val isConnected by internetMonitor.isConnected
            val connectionType by internetMonitor.connectionType
            val discoveredPeers by remember {
                derivedStateOf { neighbourService?.discoveredPeers ?: emptyList() }
            }
            val routingStates by remember {
                derivedStateOf { routingRepository.routingTable.values.toList() }
            }

            val activeFlows = remember { mutableStateListOf<NatEntry>() }

            LaunchedEffect(Unit) {
                while (true) {
                    activeFlows.clear()
                    packetForwarder
                        .getActiveInternetFlows()
                        .values
                        .let { activeFlows.addAll(it) }
                    delay(1_000)
                }
            }

            MeshlinkTheme {
                if (hasPermissions) {
                    Dashboard(
                        internetAvailable = isConnected,
                        connectionType = connectionType,
                        neighbours = discoveredPeers,
                        routingStates = routingStates,
                        activeFlows = activeFlows
                    )
                } else {
                    PermissionRequiredScreen {
                        permissionLauncher.launch(getRequiredPermissions())
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

@Composable
fun Dashboard(
    internetAvailable: Boolean,
    connectionType: String,
    neighbours: List<String>,
    routingStates: List<RoutingState>,
    activeFlows: List<NatEntry> = emptyList()
) {
    val statusText = if (internetAvailable) "Online" else "Offline"
    val statusColor = if (internetAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("MeshLink", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Decentralized mobile mesh network", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatusChip(statusText, statusColor)
                        StatusChip(connectionType, MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            SectionCard(title = "Nearby Nodes") {
                if (neighbours.isEmpty()) EmptyState("No nearby nodes found")
                else neighbours.forEach { node ->
                    AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
                        ListRow(node)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            SectionCard(title = "Routing State") {
                if (routingStates.isEmpty()) EmptyState("No routing data available")
                else routingStates.forEach { state ->
                    AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
                        RoutingStateCard(state)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            SectionCard(title = "Active Internet Flows") {
                if (activeFlows.isEmpty()) EmptyState("No nodes currently using internet")
                else activeFlows.forEach { entry ->
                    AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
                        ListRow("${entry.sourceNodeId} → ${entry.destIp}:${entry.destPort}")
                    }
                }
            }
        }
    }
}

/* ---------------- UI HELPERS ---------------- */
@Composable
fun StatusChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = MaterialTheme.shapes.large, color = color.copy(alpha = 0.12f)) {
        Text(text = label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), color = color)
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun EmptyState(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun ListRow(title: String) {
    Row(modifier = Modifier.padding(vertical = 6.dp)) {
        Text("• ", modifier = Modifier.padding(end = 6.dp))
        Text(title)
    }
}

@Composable
fun RoutingStateCard(state: RoutingState) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(state.nodeId, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Text("Latency: ${state.averageLatencyMs} ms")
            Text("Stability: ${state.stabilityScore.toInt()}%")
        }
    }
}

@Composable
fun PermissionRequiredScreen(onRequestPermission: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.Center) {
            Text("Permission Required", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text("MeshLink needs location and nearby devices permission to discover nearby phones.")
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onRequestPermission) { Text("Grant Permission") }
        }
    }
}
