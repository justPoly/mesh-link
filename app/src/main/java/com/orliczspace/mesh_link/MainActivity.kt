package com.orliczspace.mesh_link

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.orliczspace.mesh_link.network.*
import com.orliczspace.mesh_link.ui.theme.MeshlinkTheme
import java.net.InetAddress

class MainActivity : ComponentActivity() {

    private lateinit var internetMonitor: InternetMonitor
    private lateinit var linkProbeService: LinkProbeService
    private lateinit var routingRepository: RoutingStateRepository
    private lateinit var packetForwarder: PacketForwarder
    private lateinit var adaptiveProbeScheduler: AdaptiveProbeScheduler
    private var neighbourService: NeighbourDiscoveryService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        internetMonitor = InternetMonitor(this)

        linkProbeService = LinkProbeService(
            localNodeId = Build.MODEL ?: "unknown-node"
        )
        linkProbeService.start()

        adaptiveProbeScheduler = AdaptiveProbeScheduler(linkProbeService)
        routingRepository = RoutingStateRepository(linkProbeService)
        packetForwarder = PacketForwarder(
            localNodeId = Build.MODEL ?: "unknown-node",
            routingRepository = routingRepository,
            linkProbeService = linkProbeService
        )


        setContent {

            /* ---------------- Permissions ---------------- */

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

            /* ---------------- Discovery lifecycle ---------------- */

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

            LaunchedEffect(neighbourService?.connectedPeers) {
                neighbourService?.connectedPeers?.forEach { (nodeId, ip) ->
                    routingRepository.updateNode(
                        nodeId = nodeId,
                        hasInternetAccess = false
                    )

                    adaptiveProbeScheduler.startProbing(
                        nodeId = nodeId,
                        address = InetAddress.getByName(ip)
                    )
                }
            }

            /* ---------------- Observable state ---------------- */

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

            /* ---------------- Routing logic ---------------- */

            // Gateway election on probe updates
            LaunchedEffect(routingStates) {
                if (routingStates.isNotEmpty()) {
                    routingRepository.electGateway()
                }
            }

            // Route decision logging
            LaunchedEffect(routingStates) {
                val route = routingRepository.getRouteToInternet(
                    localNodeId = Build.MODEL ?: "unknown-node"
                )

                route?.let { decision ->
                    Log.d(
                        "Routing",
                        "Route to internet via ${decision.nextHopNodeId} " +
                                "(gateway=${decision.viaGateway})"
                    )
                }
            }

            /* ---------------- UI ---------------- */

            MeshlinkTheme {

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

    /* ---------------- Permissions helpers ---------------- */

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
    routingStates: List<RoutingState>
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
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(24.dp)) {

                    Text(
                        text = "MeshLink",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "Decentralized mobile mesh network",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatusChip(statusText, statusColor)
                        StatusChip(connectionType, MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            SectionCard(title = "Nearby Nodes") {
                if (neighbours.isEmpty()) {
                    EmptyState("No nearby nodes found")
                } else {
                    neighbours.forEach { node ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + expandVertically()
                        ) {
                            ListRow(node)
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            SectionCard(title = "Routing State") {
                if (routingStates.isEmpty()) {
                    EmptyState("No routing data available")
                } else {
                    routingStates.forEach { state ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + expandVertically()
                        ) {
                            RoutingStateCard(state)
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- UI helpers ---------------- */

@Composable
fun StatusChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = color
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
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
        Column(
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Permission Required", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "MeshLink needs location and nearby devices permission to discover nearby phones."
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
    }
}
