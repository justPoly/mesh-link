package com.orliczspace.mesh_link.ui.screen.permission.topology

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

import com.orliczspace.mesh_link.ui.navigation.Routes
import com.orliczspace.mesh_link.ui.scaffold.MeshScaffold

@Composable
fun TopologyScreen(
    navController: NavHostController
) {

    MeshScaffold(
        title = "Topology",
        navController = navController,
        currentRoute = Routes.Topology.route
    ) { padding ->

        TopologyContent(padding)

    }

}

@Composable
private fun TopologyContent(
    padding: PaddingValues
) {

    // Placeholder

}