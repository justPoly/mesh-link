package com.orliczspace.mesh_link.ui.components.card

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InfoCard(

    title: String,

    value: String

) {

    DashboardCard {

        Text(
            title,
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(Modifier.height(8.dp))

        Text(
            value,
            style = MaterialTheme.typography.bodyLarge
        )

    }

}