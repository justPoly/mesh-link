package com.orliczspace.mesh_link

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.orliczspace.mesh_link.network.InternetMonitor
import com.orliczspace.mesh_link.ui.theme.MeshlinkTheme

import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val internetMonitor = InternetMonitor(this)

            MeshlinkTheme {
                Dashboard(
                    internetAvailable = internetMonitor.isConnected.value,
                    connectionType = internetMonitor.connectionType.value
                )
            }
        }
    }
}

@Composable
fun Dashboard(
    internetAvailable: Boolean,
    connectionType: String
) {
    val statusText = if (internetAvailable) "Connected" else "No Internet"
    val statusColor = if (internetAvailable)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.error

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        androidx.compose.foundation.layout.Column(
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

            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.padding(12.dp)
            )

            Text(
                text = "Internet Status: $statusText",
                color = statusColor,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Connection Type: $connectionType",
                style = MaterialTheme.typography.bodyLarge
            )

            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.padding(16.dp)
            )

            Text(
                text = "Mesh Nodes",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "No nodes discovered yet",
                style = MaterialTheme.typography.bodyMedium
            )

            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.padding(16.dp)
            )

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
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = name,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MeshlinkTheme {
        Greeting("Preview")
    }
}
