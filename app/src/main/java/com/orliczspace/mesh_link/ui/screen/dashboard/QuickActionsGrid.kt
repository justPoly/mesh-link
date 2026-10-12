
package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Route
import com.orliczspace.mesh_link.ui.components.QuickActionCard


@Composable
fun QuickActionsGrid(
    onTopologyClick: () -> Unit,
    onRoutesClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            QuickActionCard(
                title = "Topology",
                icon = Icons.Default.Hub,
                onClick = onTopologyClick
            )
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            QuickActionCard(
                title = "Routes",
                icon = Icons.Default.Route,
                onClick = onRoutesClick
            )
        }
    }
}
