package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.ui.components.card.InfoCard

@Composable
fun RecentActivitySection() {

    Column(
        modifier = Modifier.padding(20.dp)
    ) {

        Text(

            "Recent Activity",

            style = MaterialTheme.typography.titleLarge

        )

        Spacer(Modifier.height(16.dp))

        InfoCard(

            title = "Last Connection",

            value = "Connected to Node Alpha"

        )

        Spacer(Modifier.height(12.dp))

        InfoCard(

            title = "Internet Gateway",

            value = "Available"

        )

    }

}