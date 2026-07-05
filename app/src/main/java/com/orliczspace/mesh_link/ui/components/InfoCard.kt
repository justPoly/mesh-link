package com.orliczspace.mesh_link.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun InfoCard(

    title: String,

    body: String

) {

    DashboardCard {

        Text(

            title,

            style = MaterialTheme.typography.titleMedium

        )

        Text(

            body,

            style = MaterialTheme.typography.bodyMedium

        )

    }

}