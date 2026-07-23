package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.ui.components.card.QuickActionCard
import com.orliczspace.mesh_link.ui.model.DashboardAction

@Composable
fun QuickActionsGrid() {

    val actions = listOf(
        DashboardAction("Discover", Icons.Default.Devices),
        DashboardAction("Connect", Icons.Default.Wifi),
        DashboardAction("Topology", Icons.Default.Hub),
        DashboardAction("Routes", Icons.Default.Route)
    )

    LazyVerticalGrid(

        columns = GridCells.Fixed(2),

        modifier = Modifier.height(220.dp),

        horizontalArrangement = Arrangement.spacedBy(12.dp),

        verticalArrangement = Arrangement.spacedBy(12.dp)

    ) {

        items(actions) {

            QuickActionCard(

                title = it.title,

                icon = it.icon

            ) {}

        }

    }

}