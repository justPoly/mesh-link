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
import androidx.compose.ui.graphics.vector.ImageVector
import com.orliczspace.mesh_link.ui.components.card.QuickActionCard

private data class Action(

    val title: String,

    val icon: ImageVector

)

@Composable
fun QuickActionsGrid() {

    val actions = listOf(

        Action("Discover", Icons.Default.Devices),

        Action("Connect", Icons.Default.Wifi),

        Action("Topology", Icons.Default.Hub),

        Action("Routes", Icons.Default.Route)

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