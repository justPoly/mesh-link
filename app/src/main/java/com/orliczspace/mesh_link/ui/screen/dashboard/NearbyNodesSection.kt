
package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.network.LinkProbeService
import com.orliczspace.mesh_link.network.NeighbourDiscoveryService
import com.orliczspace.mesh_link.network.RoutingStateRepository
import kotlinx.coroutines.delay

private data class NearbyNode(
    val id: String,
    val address: String?,
    val latency: Long?,
    val isConnected: Boolean
)

@Composable
fun NearbyNodesSection(
    neighbourService: NeighbourDiscoveryService,
    linkProbeService: LinkProbeService,
    routingRepository: RoutingStateRepository
) {
    // LinkProbeService's peer map is not Compose-observable.
    // Periodically refresh it so changes appear on both phones.
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(
        neighbourService,
        linkProbeService,
        routingRepository
    ) {
        while (true) {
            delay(1_000)
            refreshTick++
        }
    }

    val currentTick = refreshTick

    val probePeers = remember(currentTick, linkProbeService) {
        linkProbeService.getKnownPeers().toMap()
    }

    // These are Compose state collections.
    val routingPeers = routingRepository.routingTable.toMap()
    val discoveredPeers = neighbourService.discoveredPeers.toList()

    val nodes = buildList {
        val addedIds = mutableSetOf<String>()

        // Mesh peers with a node ID and known IP address.
        probePeers.forEach { (nodeId, address) ->
            if (addedIds.add(nodeId)) {
                add(
                    NearbyNode(
                        id = nodeId,
                        address = address,
                        latency = linkProbeService
                            .getAverageRtt(nodeId)
                            ?.toLong(),
                        isConnected = true
                    )
                )
            }
        }

        // Nodes already present in the routing table.
        routingPeers.forEach { (nodeId, state) ->
            if (addedIds.add(nodeId)) {
                add(
                    NearbyNode(
                        id = nodeId,
                        address = probePeers[nodeId],
                        latency = state.averageLatencyMs
                            .takeIf { it >= 0 }
                            ?.toLong(),
                        isConnected = true
                    )
                )
            }
        }

        // Wi-Fi Direct devices that have been discovered but
        // are not yet confirmed as active mesh peers.
        discoveredPeers.forEach { deviceName ->
            if (addedIds.add(deviceName)) {
                add(
                    NearbyNode(
                        id = deviceName,
                        address = null,
                        latency = null,
                        isConnected = false
                    )
                )
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Nearby Nodes",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "${nodes.size} found",
                style = MaterialTheme.typography.labelLarge,
                color = Color.LightGray
            )
        }

        if (nodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF101D25),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )

                    Spacer(modifier = Modifier.size(10.dp))

                    Text(
                        text = "No nearby nodes detected",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.size(4.dp))

                    Text(
                        text = "Keep discovery running while Mesh-link searches for peers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        } else {
            nodes.take(5).forEach { node ->
                NearbyNodeCard(node)
            }

            if (nodes.size > 5) {
                Text(
                    text = "Showing 5 of ${nodes.size} nodes",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    color = Color(0xFF4ADE80),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun NearbyNodeCard(node: NearbyNode) {
    val statusColor = if (node.isConnected) {
        Color(0xFF4ADE80)
    } else {
        Color(0xFFFFB020)
    }

    val statusText = if (node.isConnected) {
        "Mesh peer detected"
    } else {
        "Wi-Fi Direct discovered"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF101D25),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = Color(0xFF182848),
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = Color(0xFF60A5FA)
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.id,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                node.address?.let { address ->
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(statusColor, CircleShape)
                    )

                    Spacer(modifier = Modifier.size(6.dp))

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }

            node.latency?.let { latency ->
                Column(horizontalAlignment = Alignment.End) {
                    Icon(
                        imageVector = Icons.Default.SignalCellularAlt,
                        contentDescription = "Probe signal",
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(18.dp)
                    )

                    Text(
                        text = "$latency ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}
