package com.orliczspace.mesh_link.ui.screen.permission.dashboard

import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.ui.components.card.MetricCard

private data class Metric(

    val title: String,

    val value: String

)

@Composable
fun MetricsGrid() {

    val metrics = listOf(

        Metric("Nodes", "6"),

        Metric("Gateway", "2"),

        Metric("Latency", "34ms"),

        Metric("Internet", "Online")

    )

    LazyVerticalGrid(

        columns = GridCells.Fixed(2),

        modifier = Modifier.height(220.dp),

        horizontalArrangement = Arrangement.spacedBy(12.dp),

        verticalArrangement = Arrangement.spacedBy(12.dp)

    ) {

        items(metrics) {

            MetricCard(

                title = it.title,

                value = it.value

            )

        }

    }

}