package com.orliczspace.mesh_link.ui.screen.mesh_link.discovery

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

import com.orliczspace.mesh_link.ui.navigation.Routes
import com.orliczspace.mesh_link.ui.scaffold.MeshScaffold

@Composable
fun AvailableNodesScreen(
    navController: NavHostController
) {

    MeshScaffold(
        title = "Available Nodes",
        navController = navController,
        currentRoute = Routes.Nodes.route
    ) { padding ->

        AvailableNodesContent(padding)

    }

}

@Composable
private fun AvailableNodesContent(
    padding: PaddingValues
) {

    // Placeholder

}