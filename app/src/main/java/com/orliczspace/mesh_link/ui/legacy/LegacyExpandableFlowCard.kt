package com.orliczspace.mesh_link.ui.legacy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.ui.model.UiFlow

@Composable
fun ExpandableFlowCard(flow: UiFlow) {

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {

        Column(
            modifier = Modifier
                .padding(16.dp)
                .semantics { contentDescription = "Internet flow details" }
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text(
                    "${flow.sourceNode} → ${flow.destinationIp}",
                    style = MaterialTheme.typography.bodyLarge
                )

                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Details")
                }
            }

            AnimatedVisibility(expanded) {

                Column {

                    Spacer(Modifier.height(8.dp))

                    Text("Destination Port: ${flow.destinationPort}")
                }
            }
        }
    }
}