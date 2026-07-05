package com.orliczspace.mesh_link.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.network.RoutingState
import com.orliczspace.mesh_link.ui.legacy.ErrorBanner
import com.orliczspace.mesh_link.ui.legacy.ExpandableFlowCard
import com.orliczspace.mesh_link.ui.legacy.RoutingStateCard
import com.orliczspace.mesh_link.ui.legacy.SectionCard
import com.orliczspace.mesh_link.ui.model.UiFlow

@Composable
fun DashboardScreen(
    internetAvailable: Boolean,
    connectionType: String,
    neighbours: List<String>,
    routingStates: List<RoutingState>,
    activeFlows: List<UiFlow>,
    showDebug: Boolean = false
) {

    Surface(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier.padding(20.dp)
        ) {

            Text(
                "MeshLink",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(Modifier.height(10.dp))

            Text("Connection: $connectionType")

            AnimatedVisibility(!internetAvailable) {

                ErrorBanner("Internet connection lost. Mesh routing only.")
            }

            Spacer(Modifier.height(16.dp))

            SectionCard("Nearby Nodes") {

                if (neighbours.isEmpty()) {

                    Text("No nearby nodes")

                } else {

                    neighbours.forEach {
                        Text("• $it")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionCard("Routing State") {

                routingStates.forEach {

                    RoutingStateCard(it)
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionCard("Active Internet Flows") {

                activeFlows.forEach {

                    ExpandableFlowCard(it)
                }
            }

        }
    }
}