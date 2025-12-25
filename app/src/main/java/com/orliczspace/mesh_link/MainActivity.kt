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
import com.orliczspace.mesh_link.ui.theme.MeshlinkTheme

class MainActivity : ComponentActivity() {

    // Hold references at Activity level
    private lateinit var internetMonitor: InternetMonitor
    private lateinit var linkProbeService: LinkProbeService
    private var neighbourService: NeighbourDiscoveryService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize services ONCE
        internetMonitor = InternetMonitor(this)

        linkProbeService = LinkProbeService(
            localNodeId = Build.MODEL ?: "unknown-node"
        )
        linkProbeService.start()

        setContent {
            var hasRequiredPermissions by remember {
                mutableStateOf(checkRequiredPermissions())
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) {
                hasRequiredPermissions = checkRequiredPermissions()
            }

            // Request permissions on launch
            LaunchedEffect(Unit) {
                if (!hasRequiredPermissions) {
                    permissionLauncher.launch(getRequiredPermissions())
                }
            }

            // Start neighbour discovery when permissions are granted
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

            val isConnected by internetMonitor.isConnected
            val connectionType by internetMonitor.connectionType
            val discoveredPeers by remember {
                derivedStateOf { neighbourService?.discoveredPeers ?: emptyList() }
            }

            MeshlinkTheme {
                if (hasRequiredPermissions) {
                    Dashboard(
                        internetAvailable = isConnected,
                        connectionType = connectionType,
                        neighbours = discoveredPeers
                    )
                } else {
                    PermissionRequiredScreen {
                        permissionLauncher.launch(getRequiredPermissions())
                    }
                }
            }
        }
    }

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
        linkProbeService.stop()
        neighbourService = null
    }
}

/* ---------------- UI COMPOSABLES ---------------- */

@Composable
fun Dashboard(
    internetAvailable: Boolean,
    connectionType: String,
    neighbours: List<String>
) {
    val statusText = if (internetAvailable) "Connected" else "No Internet"
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
                .padding(24.dp)
        ) {
            Text("MeshLink", style = MaterialTheme.typography.headlineLarge)
            Text("Smartphone-Based Mesh Network", style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(16.dp))

            Text("Internet Status: $statusText", color = statusColor)
            Text("Connection Type: $connectionType")

            Spacer(modifier = Modifier.height(20.dp))

            Text("Mesh Nodes (${neighbours.size})", style = MaterialTheme.typography.titleMedium)

            if (neighbours.isEmpty()) {
                Text("No nodes discovered yet")
            } else {
                neighbours.forEach { node ->
                    Text("• $node")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("Routing Engine", style = MaterialTheme.typography.titleMedium)
            Text("Idle")

            Spacer(modifier = Modifier.height(16.dp))

            Text("Routing State", style = MaterialTheme.typography.titleMedium)

            if (neighbours.isEmpty()) {
                Text("No routing data available")
            } else {
                neighbours.forEach { node ->
                    RoutingStateRow(
                        nodeName = node,
                        linkQuality = "Probing…",
                        latencyMs = "-"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun RoutingStateRow(
    nodeName: String,
    linkQuality: String,
    latencyMs: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(nodeName, style = MaterialTheme.typography.bodyLarge)
            Text("Link Quality: $linkQuality")
            Text("Latency: $latencyMs ms")
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
