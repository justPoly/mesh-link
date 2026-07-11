package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.ui.components.NodeCard

@Composable
fun NearbyNodesSection() {

    Text(
        "Nearby Nodes",
        style = MaterialTheme.typography.titleLarge
    )

    Spacer(Modifier.height(16.dp))

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        NodeCard(
            nodeName = "Samsung S24",
            signal = 92,
            distance = "4 m"
        ) {}

        NodeCard(
            nodeName = "Pixel 8",
            signal = 74,
            distance = "8 m"
        ) {}

        NodeCard(
            nodeName = "OnePlus 13",
            signal = 61,
            distance = "14 m"
        ) {}

    }

}