package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun DashboardHeader() {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column {

            Text(
                text = "MeshLink",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "Decentralized Network",
                style = MaterialTheme.typography.bodyMedium
            )

        }

        IconButton(onClick = {}) {

            Icon(
                Icons.Outlined.Notifications,
                contentDescription = null
            )

        }

    }

}