package com.orliczspace.mesh_link.ui.screen.discovery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController

import com.orliczspace.mesh_link.ui.navigation.Routes
import com.orliczspace.mesh_link.ui.scaffold.MeshScaffold

@Composable
fun NodeDiscoveryScreen(
    navController: NavHostController
) {

    MeshScaffold(
        title = "Discover Nodes",
        navController = navController,
        currentRoute = Routes.Discovery.route
    ) { padding ->

        DiscoveryContent(padding)

    }

}

@Composable
private fun DiscoveryContent(
    padding: PaddingValues
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {

        Text("Discovery Screen")

    }

}