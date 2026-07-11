package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.ui.components.QuickActionCard

@Composable
fun QuickActionsSection() {

    Text(
        "Quick Actions",
        style = MaterialTheme.typography.titleLarge
    )

    Spacer(Modifier.height(16.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        QuickActionCard(
            title = "Discover Nodes"
        ) {}

        QuickActionCard(
            title = "Find Route"
        ) {}

    }

}