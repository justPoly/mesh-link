package com.orliczspace.mesh_link.ui.components.card

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StatusCard(

    status: String,

    online: Boolean

) {

    DashboardCard {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(

                imageVector =
                if (online)
                    Icons.Default.CheckCircle
                else
                    Icons.Default.Error,

                contentDescription = null

            )

            Spacer(Modifier.width(16.dp))

            Column {

                Text(
                    "Mesh Status",
                    style = MaterialTheme.typography.labelLarge
                )

                Text(
                    status,
                    style = MaterialTheme.typography.headlineSmall
                )

            }

        }

    }

}