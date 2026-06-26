package com.orliczspace.mesh_link.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.network.RoutingState

@Composable
fun RoutingStateCard(state: RoutingState) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(state.nodeId, style = MaterialTheme.typography.bodyLarge)

            Spacer(Modifier.height(6.dp))

            Text("Latency: ${state.averageLatencyMs} ms")
            Text("Stability: ${state.stabilityScore.toInt()} %")
            Text("Internet Access: ${state.hasInternetAccess}")
        }
    }
}