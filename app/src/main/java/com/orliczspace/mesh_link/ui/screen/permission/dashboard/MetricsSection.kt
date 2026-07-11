package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.ui.components.MetricCard

@Composable
fun MetricsSection() {

    Text(
        "Network Metrics",
        style = MaterialTheme.typography.titleLarge
    )

    Spacer(Modifier.height(16.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        MetricCard(
            title = "Signal",
            value = "-52 dBm",
            icon = Icons.Default.Wifi
        )

        MetricCard(
            title = "Latency",
            value = "28 ms",
            icon = Icons.Default.Speed
        )

    }

}