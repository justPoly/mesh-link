package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
fun MetricsGrid() {

    val metrics = listOf(

        DashboardMetric(
            title = "Connected Nodes",
            value = 24,
            icon = Icons.Default.Devices,
            color = Color(0xFF4ADE80)
        ),

        DashboardMetric(
            title = "Latency",
            value = 18,
            icon = Icons.Default.Speed,
            color = Color(0xFFFFB020)
        ),

        DashboardMetric(
            title = "Gateways",
            value = 2,
            icon = Icons.Default.Router,
            color = Color(0xFF60A5FA)
        ),

        DashboardMetric(
            title = "Internet",
            value = 1,
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
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(

            columns = GridCells.Adaptive(minSize = 170.dp),

            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp),

            horizontalArrangement = Arrangement.spacedBy(16.dp),

            verticalArrangement = Arrangement.spacedBy(16.dp),

            userScrollEnabled = false

        ) {

            itemsIndexed(metrics) { index, metric ->

                AnimatedMetricCard(

                    title = metric.title,

                    value = metric.value,

                    icon = metric.icon,

                    color = metric.color,

                    animationDelay = index * 150L

                )

            }

        }

    }

}