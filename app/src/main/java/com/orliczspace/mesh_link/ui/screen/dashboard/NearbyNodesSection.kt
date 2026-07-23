package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.ui.components.card.NodeCard

@Composable
fun NearbyNodesSection() {

    Column(
        modifier = Modifier.padding(20.dp)
    ) {

        Text(

            "Nearby Nodes",

            style = MaterialTheme.typography.titleLarge

        )

        Spacer(Modifier.height(16.dp))

        NodeCard(

            nodeName = "Node Alpha",

            status = "Gateway"

        )

        Spacer(Modifier.height(12.dp))

        NodeCard(

            nodeName = "Node Beta",

            status = "Connected"

        )

    }

}