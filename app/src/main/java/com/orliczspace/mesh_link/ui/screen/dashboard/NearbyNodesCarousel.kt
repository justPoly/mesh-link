package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.ui.components.card.NodeCard
import com.orliczspace.mesh_link.ui.model.DashboardNode

@Composable
fun NearbyNodesCarousel() {

    val nodes = listOf(

        DashboardNode(
            "Galaxy S24",
            "Gateway"
        ),

        DashboardNode(
            "Pixel 9",
            "Connected"
        ),

        DashboardNode(
            "Laptop",
            "Nearby"
        ),

        DashboardNode(
            "Tablet",
            "Weak"
        )
    )

    Column {

        Text(
            text = "Nearby Nodes",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(
            modifier = Modifier.width(12.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            items(nodes) { node ->

                Box(
                    modifier = Modifier.width(260.dp)
                ) {

                    NodeCard(
                        nodeName = node.name,
                        status = node.state
                    )
                }
            }
        }
    }
}