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
import com.orliczspace.mesh_link.ui.theme.MeshlinkTheme

class MainActivity : ComponentActivity() {

    // Hold references at Activity level to survive recompositions
    private lateinit var internetMonitor: InternetMonitor
    private var neighbourService: NeighbourDiscoveryService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create monitors once (not inside Compose)
        internetMonitor = InternetMonitor(this)

        setContent {
            var hasRequiredPermissions by remember {
                mutableStateOf(checkRequiredPermissions())
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                hasRequiredPermissions = checkRequiredPermissions()
            }

            // Automatically request permissions on launch if missing
            LaunchedEffect(Unit) {
                if (!hasRequiredPermissions) {
                    permissionLauncher.launch(getRequiredPermissions())
                }
            }

            // Start neighbour discovery only when permissions are granted
            LaunchedEffect(hasRequiredPermissions) {
                if (hasRequiredPermissions) {
                    if (neighbourService == null) {
                        neighbourService = NeighbourDiscoveryService(this@MainActivity)
                        neighbourService?.startDiscovery()
                    }
                } else {
                    // Optional: stop discovery if permission revoked (rare)
                    neighbourService?.stopDiscovery()
                    neighbourService = null
                }
            }

            val isConnected by internetMonitor.isConnected
            val connectionType by internetMonitor.connectionType
            val discoveredPeers by remember { derivedStateOf { neighbourService?.discoveredPeers ?: emptyList() } }

            MeshlinkTheme {
                if (hasRequiredPermissions) {
                    Dashboard(
                        internetAvailable = isConnected,
                        connectionType = connectionType,
                        neighbours = discoveredPeers
                    )
                } else {
                    PermissionRequiredScreen(onRequestPermission = {
                        permissionLauncher.launch(getRequiredPermissions())
                    })
                }
            }
        }
    }

    private fun checkRequiredPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val nearbyDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

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
    val statusColor = if (internetAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(text = "MeshLink", style = MaterialTheme.typography.headlineLarge)
            Text(text = "Smartphone-Based Mesh Network", style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Internet Status: $statusText", color = statusColor, style = MaterialTheme.typography.titleMedium)
            Text(text = "Connection Type: $connectionType", style = MaterialTheme.typography.bodyLarge)

            Spacer(modifier = Modifier.height(20.dp))

            Text(text = "Mesh Nodes (${neighbours.size})", style = MaterialTheme.typography.titleMedium)

            if (neighbours.isEmpty()) {
                Text(text = "No nodes discovered yet", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column {
                    neighbours.forEach { node ->
                        Text(text = "• $node", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(text = "Routing Engine", style = MaterialTheme.typography.titleMedium)
            Text(text = "Idle", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun PermissionRequiredScreen(onRequestPermission: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text(
                text = "Permission Required",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "MeshLink needs location and nearby devices permission to discover nearby phones via Wi-Fi Direct.\n\n" +
                        "Your location is NOT tracked or stored — it's only used for device discovery.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
    }
}