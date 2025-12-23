package com.orliczspace.mesh_link

import android.Manifest
import android.content.pm.PackageManager
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
import com.orliczspace.mesh_link.ui.theme.MeshlinkTheme

class MainActivity : ComponentActivity() {

    private lateinit var internetMonitor: InternetMonitor
    private var neighbourService: NeighbourDiscoveryService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            // Create Internet monitor once
            internetMonitor = remember { InternetMonitor(this) }

            // Track location permission state
            var hasLocationPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }

            // Permission launcher (Compose-safe)
            val permissionLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    hasLocationPermission =
                        permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                }

            // Request permission & start discovery
            LaunchedEffect(hasLocationPermission) {
                if (!hasLocationPermission) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                } else {
                    if (neighbourService == null) {
                        neighbourService = NeighbourDiscoveryService(this@MainActivity)
                        neighbourService?.startDiscovery()
                    }
                }
            }

            val isConnected by internetMonitor.isConnected
            val connectionType by internetMonitor.connectionType

            MeshlinkTheme {
                if (hasLocationPermission) {
                    Dashboard(
                        internetAvailable = isConnected,
                        connectionType = connectionType,
                        neighbours = neighbourService?.discoveredPeers ?: emptyList()
                    )
                } else {
                    PermissionRequiredScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        internetMonitor.close()
        neighbourService?.stopDiscovery()
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

            Text(
                text = "MeshLink",
                style = MaterialTheme.typography.headlineLarge
            )

            Text(
                text = "Smartphone-Based Mesh Network",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Internet Status: $statusText",
                color = statusColor,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Connection Type: $connectionType",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Mesh Nodes (${neighbours.size})",
                style = MaterialTheme.typography.titleMedium
            )

            if (neighbours.isEmpty()) {
                Text(
                    text = "No nodes discovered yet",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                neighbours.forEach { node ->
                    Text("• $node")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Routing Engine",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Idle",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun PermissionRequiredScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Permission Required",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Location permission is required to discover nearby mesh nodes using Wi-Fi Direct. " +
                        "Your location is not stored or tracked.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
