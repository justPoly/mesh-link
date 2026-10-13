
package com.orliczspace.mesh_link.ui.screen.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.orliczspace.mesh_link.network.LinkProbeService
import com.orliczspace.mesh_link.network.NeighbourDiscoveryService
import com.orliczspace.mesh_link.ui.navigation.Routes
import com.orliczspace.mesh_link.ui.scaffold.MeshScaffold
import kotlinx.coroutines.delay

@Composable
fun NodeDiscoveryScreen(
    navController: NavHostController,
    linkProbeService: LinkProbeService,
    neighbourService: NeighbourDiscoveryService
) {
    var refreshTick by remember { mutableIntStateOf(0) }
    var isRequestingDiscovery by remember { mutableStateOf(false) }
    var discoveryMessage by remember { mutableStateOf<String?>(null) }

    // Refresh the displayed peer registry while this screen is visible.
    LaunchedEffect(Unit) {
        while (true) {
            refreshTick++
            delay(1000)
        }
    }

    // Start a discovery attempt when the screen is first opened.
    LaunchedEffect(linkProbeService, neighbourService) {
        try {
            linkProbeService.requestDiscovery()
            neighbourService.startDiscovery()
        } catch (e: Exception) {
            discoveryMessage =
                "Could not start all discovery methods: " +
                        (e.message ?: e.javaClass.simpleName)
        }
    }

    val udpPeers = remember(refreshTick) {
        linkProbeService.getKnownPeers().toMap()
    }

    val wifiDirectPeers = remember(refreshTick) {
        neighbourService.discoveredPeers.toList()
    }

    val wifiDirectStatus = remember(refreshTick) {
        neighbourService.discoveryStatus.toString()
    }

    MeshScaffold(
        title = "Discover Nodes",
        navController = navController,
        currentRoute = Routes.Discovery.route
    ) { padding ->
        DiscoveryContent(
            padding = padding,
            udpPeers = udpPeers,
            wifiDirectPeers = wifiDirectPeers,
            wifiDirectStatus = wifiDirectStatus,
            isRequestingDiscovery = isRequestingDiscovery,
            discoveryMessage = discoveryMessage,
            onDiscoverClick = {
                isRequestingDiscovery = true
                discoveryMessage = null

                try {
                    linkProbeService.requestDiscovery()
                    neighbourService.startDiscovery()
                    discoveryMessage = "Discovery requested. Waiting for nearby nodes."
                } catch (e: Exception) {
                    discoveryMessage =
                        "Discovery could not start: " +
                                (e.message ?: e.javaClass.simpleName)
                } finally {
                    isRequestingDiscovery = false
                }
            }
        )
    }
}

@Composable
private fun DiscoveryContent(
    padding: PaddingValues,
    udpPeers: Map<String, String>,
    wifiDirectPeers: List<String>,
    wifiDirectStatus: String,
    isRequestingDiscovery: Boolean,
    discoveryMessage: String?,
    onDiscoverClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Nearby Mesh Nodes",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "${udpPeers.size} UDP peer(s) discovered",
            style = MaterialTheme.typography.titleMedium
        )

        Button(
            onClick = onDiscoverClick,
            enabled = !isRequestingDiscovery,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isRequestingDiscovery) {
                CircularProgressIndicator()
            } else {
                Text("Discover Nodes")
            }
        }

        discoveryMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Wi-Fi Direct",
                    style = MaterialTheme.typography.titleMedium
                )
                Text("Status: $wifiDirectStatus")
                Text("Discovered device names: ${wifiDirectPeers.size}")
            }
        }

        if (udpPeers.isEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "No UDP mesh nodes discovered yet. Keep both phones " +
                        "connected to the same reachable Wi-Fi network and wait " +
                        "for discovery responses.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(
                text = "Discovered Peers",
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = udpPeers.entries.toList(),
                    key = { it.key }
                ) { peer ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Node ${peer.key}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "IP address: ${peer.value}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Discovered via UDP",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
