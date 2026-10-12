package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class DashboardLoadingState {
    INITIALIZING,
    DISCOVERING_PEERS,
    ESTABLISHING_MESH,
    SELECTING_GATEWAY,
    CONNECTING
}

@Composable
fun DashboardLoadingSection(
    state: DashboardLoadingState
) {
    val (title, description) = when (state) {
        DashboardLoadingState.INITIALIZING -> {
            "Initializing" to
                    "Preparing Mesh-link networking services."
        }

        DashboardLoadingState.DISCOVERING_PEERS -> {
            "Discovering peers" to
                    "Searching for nearby devices using Wi-Fi Direct."
        }

        DashboardLoadingState.ESTABLISHING_MESH -> {
            "Establishing mesh" to
                    "Peers have been detected. Waiting for usable mesh routes."
        }

        DashboardLoadingState.SELECTING_GATEWAY -> {
            "Selecting gateway" to
                    "Routing information is available. Waiting for gateway election."
        }

        DashboardLoadingState.CONNECTING -> {
            "Connecting" to
                    "A gateway is selected. Checking Internet connectivity."
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF101D25),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Color(0xFF60A5FA),
                strokeWidth = 3.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }
        }
    }
}