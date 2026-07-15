package com.orliczspace.mesh_link.ui.components.card

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MetricCard(

    title: String,

    value: String

) {

    DashboardCard {

        Text(
            title,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(8.dp))

        Text(
            value,
            style = MaterialTheme.typography.headlineMedium
        )

    }

}