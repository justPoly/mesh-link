package com.orliczspace.mesh_link.ui.components.card

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.ui.components.badge.MeshBadge
import com.orliczspace.mesh_link.ui.components.signal.SignalStrength

@Composable
fun NodeCard(

    nodeName: String,

    status: String

) {

    DashboardCard {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            SignalStrength()

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    nodeName,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall
                )

            }

            MeshBadge(status)

        }

    }

}