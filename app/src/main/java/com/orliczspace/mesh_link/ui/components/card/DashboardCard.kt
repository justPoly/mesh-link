package com.orliczspace.mesh_link.ui.components.card

import androidx.compose.runtime.Composable

@Composable
fun DashboardCard(
    content: @Composable () -> Unit
) {
    MeshCard {
        content()
    }
}