package com.orliczspace.mesh_link.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector
) {

    MeshCard {

        Icon(
            icon,
            null,
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(10.dp))

        Text(
            title,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(6.dp))

        Text(
            value,
            style = MaterialTheme.typography.headlineSmall
        )

    }

}