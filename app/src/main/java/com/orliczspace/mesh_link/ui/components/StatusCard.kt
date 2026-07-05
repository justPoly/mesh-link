package com.orliczspace.mesh_link.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StatusCard(

    online: Boolean,

    connectionType: String

) {

    DashboardCard {

        Row(

            verticalAlignment = Alignment.CenterVertically,

            horizontalArrangement = Arrangement.SpaceBetween,

            modifier = Modifier.fillMaxWidth()

        ) {

            Column {

                Text(

                    if (online) "Online"

                    else "Offline",

                    style = MaterialTheme.typography.headlineMedium

                )

                Text(connectionType)

            }

            Icon(

                Icons.Default.Wifi,

                contentDescription = null

            )

        }

    }

}