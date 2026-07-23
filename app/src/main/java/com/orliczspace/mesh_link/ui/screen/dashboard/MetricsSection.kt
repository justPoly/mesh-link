package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.ui.components.card.MetricCard

@Composable
fun MetricsSection() {

    Column(
        modifier = Modifier.padding(horizontal = 20.dp)
    ) {

        Row {

            MetricCard(

                title = "Nodes",

                value = "5"

            )

            Spacer(Modifier.width(12.dp))

            MetricCard(

                title = "Gateways",

                value = "2"

            )

        }

        Spacer(Modifier.height(12.dp))

        Row {

            MetricCard(

                title = "Latency",

                value = "34 ms"

            )

            Spacer(Modifier.width(12.dp))

            MetricCard(

                title = "Internet",

                value = "Yes"

            )

        }

    }

}