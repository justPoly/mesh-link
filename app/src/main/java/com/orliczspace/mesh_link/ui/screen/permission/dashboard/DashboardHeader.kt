package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DashboardHeader() {

    Column(
        modifier = Modifier.padding(20.dp)
    ) {

        Text(
            text = "Welcome",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "MeshLink Node",
            style = MaterialTheme.typography.headlineMedium
        )

    }

}