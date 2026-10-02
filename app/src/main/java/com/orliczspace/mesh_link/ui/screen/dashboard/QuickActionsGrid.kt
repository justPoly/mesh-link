package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.ui.components.QuickActionCard
import com.orliczspace.mesh_link.ui.model.DashboardAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Wifi

@Composable
fun QuickActionsGrid(
    onFindNodesClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    onTopologyClick: () -> Unit,
    onRoutesClick: () -> Unit
) {
    val actions = listOf(
        DashboardAction(
            title = "Find Nodes",
            icon = Icons.Default.Devices
        ),
        DashboardAction(
            title = "Diagnostics",
            icon = Icons.Default.Wifi
        ),
        DashboardAction(
            title = "Topology",
            icon = Icons.Default.Hub
        ),
        DashboardAction(
            title = "Routes",
            icon = Icons.Default.Route
        )
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                title = actions[0].title,
                icon = actions[0].icon,
                onClick = onFindNodesClick,
                modifier = Modifier.weight(1f)
            )

            QuickActionCard(
                title = actions[1].title,
                icon = actions[1].icon,
                onClick = onDiagnosticsClick,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                title = actions[2].title,
                icon = actions[2].icon,
                onClick = onTopologyClick,
                modifier = Modifier.weight(1f)
            )

            QuickActionCard(
                title = actions[3].title,
                icon = actions[3].icon,
                onClick = onRoutesClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}