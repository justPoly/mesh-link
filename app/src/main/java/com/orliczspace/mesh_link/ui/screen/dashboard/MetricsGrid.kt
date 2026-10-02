package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.ui.components.AnimatedMetricCard
import com.orliczspace.mesh_link.ui.model.DashboardMetric

@Composable
fun MetricsGrid(
    connectedNodes: Int,
    latency: Int,
    gateways: Int,
    internet: Int
) {
    val metrics = listOf(
        DashboardMetric(
            title = "Connected Nodes",
            value = connectedNodes,
            icon = Icons.Default.Devices,
            color = Color(0xFF4ADE80)
        ),
        DashboardMetric(
            title = "Latency",
            value = latency,
            icon = Icons.Default.Speed,
            color = Color(0xFFFFB020)
        ),
        DashboardMetric(
            title = "Gateways",
            value = gateways,
            icon = Icons.Default.Router,
            color = Color(0xFF60A5FA)
        ),
        DashboardMetric(
            title = "Internet",
            value = internet,
            icon = Icons.Default.Wifi,
            color = Color(0xFFEC4899)
        )
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Network Metrics",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(
                start = 4.dp,
                bottom = 16.dp
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedMetricCard(
                title = metrics[0].title,
                value = metrics[0].value,
                icon = metrics[0].icon,
                color = metrics[0].color,
                modifier = Modifier.weight(1f),
                animationDelay = 0L
            )

            AnimatedMetricCard(
                title = metrics[1].title,
                value = metrics[1].value,
                icon = metrics[1].icon,
                color = metrics[1].color,
                modifier = Modifier.weight(1f),
                animationDelay = 150L
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedMetricCard(
                title = metrics[2].title,
                value = metrics[2].value,
                icon = metrics[2].icon,
                color = metrics[2].color,
                modifier = Modifier.weight(1f),
                animationDelay = 300L
            )

            AnimatedMetricCard(
                title = metrics[3].title,
                value = metrics[3].value,
                icon = metrics[3].icon,
                color = metrics[3].color,
                modifier = Modifier.weight(1f),
                animationDelay = 450L
            )
        }
    }
}