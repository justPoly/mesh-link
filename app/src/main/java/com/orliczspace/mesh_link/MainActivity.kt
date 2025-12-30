package com.orliczspace.mesh_link

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.orliczspace.mesh_link.network.InternetMonitor
import com.orliczspace.mesh_link.network.NeighbourDiscoveryService
import com.orliczspace.mesh_link.network.LinkProbeService
import com.orliczspace.mesh_link.network.RoutingStateRepository
import com.orliczspace.mesh_link.network.AdaptiveProbeScheduler
import com.orliczspace.mesh_link.ui.theme.MeshlinkTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

// ✅ NEW IMPORT
import com.google.accompanist.systemuicontroller.rememberSystemUiController


class MainActivity : ComponentActivity() {

    // Activity-level services
    private lateinit var internetMonitor: InternetMonitor
    private lateinit var linkProbeService: LinkProbeService
    private lateinit var routingRepository: RoutingStateRepository
    private lateinit var adaptiveProbeScheduler: AdaptiveProbeScheduler
    private var neighbourService: NeighbourDiscoveryService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Initialize core services ONCE
        internetMonitor = InternetMonitor(this)

        linkProbeService = LinkProbeService(
            localNodeId = Build.MODEL ?: "unknown-node"
        )
        linkProbeService.start()

        adaptiveProbeScheduler = AdaptiveProbeScheduler(linkProbeService)

        routingRepository = RoutingStateRepository(linkProbeService)

        setContent {

            /* ---------------- PERMISSIONS ---------------- */

            var hasRequiredPermissions by remember {
                mutableStateOf(checkRequiredPermissions())
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {
                hasRequiredPermissions = checkRequiredPermissions()
            }

            LaunchedEffect(Unit) {
                if (!hasRequiredPermissions) {
                    permissionLauncher.launch(getRequiredPermissions())
                }
            }

            /* ---------------- NEIGHBOUR DISCOVERY ---------------- */

            LaunchedEffect(hasRequiredPermissions) {
                if (hasRequiredPermissions) {
                    if (neighbourService == null) {
                        neighbourService = NeighbourDiscoveryService(this@MainActivity)
                        neighbourService?.startDiscovery()
                    }
                } else {
                    neighbourService?.stopDiscovery()
                    neighbourService = null
                }
            }

            /* ---------------- ROUTING STATE UPDATE ---------------- */

            LaunchedEffect(neighbourService?.connectedPeers) {
                neighbourService?.connectedPeers?.forEach { (nodeId, ip) ->

                    routingRepository.updateNode(
                        nodeId = nodeId,
                        hasInternetAccess = false
                    )

                    adaptiveProbeScheduler.startProbing(
                        nodeId = nodeId,
                        address = java.net.InetAddress.getByName(ip)
                    )
                }
            }

            /* ---------------- OBSERVED STATE ---------------- */

            val isConnected by internetMonitor.isConnected
            val connectionType by internetMonitor.connectionType

            val discoveredPeers by remember {
                derivedStateOf {
                    neighbourService?.discoveredPeers ?: emptyList()
                }
            }

            val routingStates by remember {
                derivedStateOf {
                    routingRepository.routingTable.values.toList()
                }
            }

            /* ---------------- UI ---------------- */

            MeshlinkTheme {

                // ✅ NEW: status bar color fix
                val systemUiController = rememberSystemUiController()
                val backgroundColor = MaterialTheme.colorScheme.background

                SideEffect {
                    systemUiController.setStatusBarColor(
                        color = backgroundColor,
                        darkIcons = true
                    )
                }

                if (hasRequiredPermissions) {
                    Dashboard(
                        internetAvailable = isConnected,
                        connectionType = connectionType,
                        neighbours = discoveredPeers,
                        routingStates = routingStates
                    )
                } else {
                    PermissionRequiredScreen {
                        permissionLauncher.launch(getRequiredPermissions())
                    }
                }
            }
        }
    }

    /* ---------------- PERMISSIONS HELPERS ---------------- */

    private fun checkRequiredPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val nearbyDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return fineLocation && coarseLocation && nearbyDevices
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
        internetMonitor.close()
        neighbourService?.stopDiscovery()
        adaptiveProbeScheduler.stopAll()
        linkProbeService.stop()
        neighbourService = null
    }

}


/* ---------------- UI COMPOSABLES ---------------- */

@Composable
fun Dashboard(
    internetAvailable: Boolean,
    connectionType: String,
    neighbours: List<String>,
    routingStates: List<com.orliczspace.mesh_link.network.RoutingState>
) {
    val statusText = if (internetAvailable) "Online" else "Offline"
    val statusColor =
        if (internetAvailable) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.error

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {

            /* ---------- HEADER ---------- */

            Text(
                text = "MeshLink",
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = "Decentralized mobile mesh network",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            /* ---------- STATUS CARD ---------- */

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Internet", style = MaterialTheme.typography.labelMedium)
                        Text(statusText, color = statusColor)
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        Text("Connection", style = MaterialTheme.typography.labelMedium)
                        Text(connectionType)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            /* ---------- NODES ---------- */

            SectionCard(title = "Discovered Nodes (${neighbours.size})") {
                if (neighbours.isEmpty()) {
                    Text("No nearby nodes found")
                } else {
                    neighbours.forEach { node ->
                        ListRow(node)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            /* ---------- ROUTING ---------- */

            SectionCard(title = "Routing State") {
                if (routingStates.isEmpty()) {
                    Text("No routing data available")
                } else {
                    routingStates.forEach { state ->
                        RoutingStateCard(state)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun ListRow(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text("•", modifier = Modifier.padding(end = 8.dp))
        Text(title)
    }
}

@Composable
fun RoutingStateCard(
    state: com.orliczspace.mesh_link.network.RoutingState
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            Text(
                text = state.nodeId,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(4.dp))

            Text("Latency: ${state.averageLatencyMs} ms")
            Text("Stability: ${state.stabilityScore.toInt()}%")
        }
    }
}

@Composable
fun PermissionRequiredScreen(onRequestPermission: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Permission Required", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "MeshLink needs location and nearby devices permission to discover nearby phones via Wi-Fi Direct."
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
    }
}
