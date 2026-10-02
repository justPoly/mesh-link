package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun StatusSection(
    isConnected: Boolean,
    connectionType: String
) {

    val cardShape = RoundedCornerShape(16.dp)

    val statusColor =
        if (isConnected) {
            Color(0xFF22C55E)
        } else {
            Color(0xFFEF4444)
        }

    val statusText =
        if (isConnected) {
            "Online"
        } else {
            "Offline"
        }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF101D25),
                shape = cardShape
            )
            .padding(18.dp)
    ) {

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Text(
                text = "Status",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column {

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = statusColor
                    )

                    if (isConnected) {

                        Text(
                            text = connectionType,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )

                    }

                }

                Box(
                    modifier = Modifier
                        .background(
                            color = statusColor,
                            shape = RoundedCornerShape(50)
                        )
                        .padding(14.dp)
                ) {

                    Icon(
                        imageVector =
                            if (isConnected) {
                                Icons.Default.Wifi
                            } else {
                                Icons.Default.WifiOff
                            },
                        contentDescription =
                            if (isConnected) {
                                "Network online"
                            } else {
                                "Network offline"
                            },
                        tint = Color.White
                    )

                }

            }

        }

    }
}