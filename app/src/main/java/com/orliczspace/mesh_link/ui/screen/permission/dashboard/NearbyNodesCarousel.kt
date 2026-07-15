package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.ui.components.card.NodeCard

private data class Node(

    val name: String,

    val state: String

)

@Composable
fun NearbyNodesCarousel() {

    val nodes = listOf(

        Node("Galaxy S24", "Gateway"),

        Node("Pixel 9", "Connected"),

        Node("Laptop", "Nearby"),

        Node("Tablet", "Weak")

    )

    Column {

        Text(

            "Nearby Nodes",

            style = MaterialTheme.typography.titleLarge

        )

        Spacer(Modifier.height(12.dp))

        LazyRow(

            horizontalArrangement = Arrangement.spacedBy(16.dp)

        ) {

            items(nodes) {

                Box(
                    modifier = Modifier.width(260.dp)
                ) {

                    NodeCard(

                        nodeName = it.name,

                        status = it.state

                    )

                }

            }

        }

    }

}