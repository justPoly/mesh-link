package com.orliczspace.mesh_link.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean
) {

    MeshCard {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                Icons.Default.LocationOn,
                null
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(title)

                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall
                )

            }

            Icon(
                Icons.Default.CheckCircle,
                null,
                tint =
                if (granted)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )

        }

    }

}