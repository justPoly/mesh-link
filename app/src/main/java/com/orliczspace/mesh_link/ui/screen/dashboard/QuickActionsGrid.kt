
package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Devices
import com.orliczspace.mesh_link.ui.components.QuickActionCard

@Composable
fun QuickActionsGrid(
    onDiscoverClick: () -> Unit,
    onNodesClick: () -> Unit,
    onTopologyClick: () -> Unit,
    onRoutesClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // First row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                QuickActionCard(
                    title = "Discover Nodes",
                    icon = Icons.Default.Search,
                    onClick = onDiscoverClick
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                QuickActionCard(
                    title = "Available Nodes",
                    icon = Icons.Default.Devices,
                    onClick = onNodesClick
                )
            }
        }

        // Second row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                QuickActionCard(
                    title = "Topology",
                    icon = Icons.Default.Hub,
                    onClick = onTopologyClick
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                QuickActionCard(
                    title = "Routes",
                    icon = Icons.Default.Route,
                    onClick = onRoutesClick
                )
            }
        }
    }
}
