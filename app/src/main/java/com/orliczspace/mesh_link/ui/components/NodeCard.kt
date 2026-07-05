package com.orliczspace.mesh_link.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NodeCard(

    nodeName: String,

    ipAddress: String,

    latency: Int,

    signal: Int,

    connected: Boolean,

    modifier: Modifier = Modifier

) {

    Card(
        modifier = modifier.fillMaxWidth()
    ) {

        Row(
            modifier = Modifier
                .padding(16.dp),

            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                Icons.Default.Router,
                contentDescription = null
            )

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    nodeName,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(ipAddress)

                Spacer(Modifier.height(6.dp))

                Text("Latency : ${latency} ms")

            }

            Column(
                horizontalAlignment = Alignment.End
            ) {

                SignalStrength(signal)

                Spacer(Modifier.height(8.dp))

                ConnectionStatus(connected)

            }

        }

    }

}