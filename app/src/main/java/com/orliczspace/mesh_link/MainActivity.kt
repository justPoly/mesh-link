package com.orliczspace.mesh_link

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.network.InternetMonitor
import com.orliczspace.mesh_link.ui.theme.MeshlinkTheme

class MainActivity : ComponentActivity() {

    private lateinit var internetMonitor: InternetMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // Create once and remember across recompositions
            internetMonitor = remember { InternetMonitor(this) }

            // These lines now compile and work perfectly!
            val isConnected by internetMonitor.isConnected
            val connectionType by internetMonitor.connectionType

            MeshlinkTheme {
                Dashboard(
                    internetAvailable = isConnected,
                    connectionType = connectionType
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::internetMonitor.isInitialized) {
            internetMonitor.close()
        }
    }
}

@Composable
fun Dashboard(
    internetAvailable: Boolean,
    connectionType: String
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
            Text(
                text = "MeshLink",
                style = MaterialTheme.typography.headlineLarge
            )

            Text(
                text = "Smartphone-Based Mesh Network",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Internet Status: $statusText",
                color = statusColor,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Connection Type: $connectionType",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Mesh Nodes",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "No nodes discovered yet",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

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

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    MeshlinkTheme {
        Dashboard(
            internetAvailable = true,
            connectionType = "Wi-Fi"
        )
    }
}