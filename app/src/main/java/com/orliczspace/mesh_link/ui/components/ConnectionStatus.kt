package com.orliczspace.mesh_link.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConnectionStatus(
    connected: Boolean
) {

    val color =
        if (connected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.error

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )

        Spacer(Modifier.width(8.dp))

        Text(
            if (connected)
                "Connected"
            else
                "Disconnected"
        )

    }
}