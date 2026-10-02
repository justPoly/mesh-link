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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.network.LinkProbeService
import com.orliczspace.mesh_link.network.NeighbourDiscoveryService
import com.orliczspace.mesh_link.network.RoutingStateRepository

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
    val probePeers = linkProbeService.getKnownPeers()
    val routingPeers = routingRepository.routingTable
    val discoveredPeers = neighbourService.discoveredPeers

    val nodes = buildList {
        val addedIds = mutableSetOf<String>()

        /*
         * LinkProbe peers are the strongest source for a known
         * mesh node because they have an actual node ID and IP.
         */
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

        /*
         * Routing nodes may contain nodes that have not yet appeared
         * in the LinkProbe peer map.
         *
         * averageLatencyMs is already Long?, so no conversion is needed.
         */
        routingPeers.forEach { (nodeId, state) ->
            if (addedIds.add(nodeId)) {
                add(
                    NearbyNode(
                        id = nodeId,
                        address = null,
                        latency = state.averageLatencyMs
                            .takeIf { it >= 0 }
                            ?.toLong(),
                        isConnected = true
                    )
                )
            }
        }

        /*
         * Wi-Fi Direct discovery can find devices before they become
         * active mesh peers. Keep them visible as discovered devices.
         */
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

            if (nodes.isNotEmpty()) {
                Text(
                    text = "${nodes.size} found",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.LightGray
                )
            }
        }

        if (nodes.isEmpty()) {
            EmptyNearbyNodesCard()
        } else {
            nodes
                .take(5)
                .forEach { node ->
                    NearbyNodeCard(node)
                }

            if (nodes.size > 5) {
                Text(
                    text = "View all nodes",
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
private fun NearbyNodeCard(
    node: NearbyNode
) {
    val statusColor =
        if (node.isConnected) {
            Color(0xFF4ADE80)
        } else {
            Color(0xFFFFB020)
        }

    val statusText =
        if (node.isConnected) {
            "Connected"
        } else {
            "Discovered"
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

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = node.id,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (node.address != null) {
                    Text(
                        text = node.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                color = statusColor,
                                shape = CircleShape
                            )
                    )

                    Spacer(modifier = Modifier.size(6.dp))

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }

            if (node.latency != null) {
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Icon(
                        imageVector = Icons.Default.SignalCellularAlt,
                        contentDescription = null,
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(18.dp)
                    )

                    Text(
                        text = "${node.latency} ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyNearbyNodesCard() {
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
                text = "Start discovery to find nearby mesh devices.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}