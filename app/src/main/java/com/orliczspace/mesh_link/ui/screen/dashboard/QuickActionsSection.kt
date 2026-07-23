package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.ui.components.card.QuickActionCard

@Composable
fun QuickActionsSection() {

    Column(
        modifier = Modifier.padding(20.dp)
    ) {

        Row {

            QuickActionCard(

                title = "Discover",

                icon = Icons.Default.Devices

            ) {}

            Spacer(Modifier.width(12.dp))

            QuickActionCard(

                title = "Connect",

                icon = Icons.Default.Wifi

            ) {}

        }

        Spacer(Modifier.height(12.dp))

        Row {

            QuickActionCard(

                title = "Topology",

                icon = Icons.Default.Hub

            ) {}

            Spacer(Modifier.width(12.dp))

            QuickActionCard(

                title = "Routes",

                icon = Icons.Default.Route

            ) {}

        }

    }

}