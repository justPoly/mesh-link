package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun QuickActionsSection(
    onFindNodesClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    onTopologyClick: () -> Unit,
    onRoutesClick: () -> Unit
) {

    Column {

        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        QuickActionsGrid(
            onFindNodesClick = onFindNodesClick,
            onDiagnosticsClick = onDiagnosticsClick,
            onTopologyClick = onTopologyClick,
            onRoutesClick = onRoutesClick
        )
    }
}