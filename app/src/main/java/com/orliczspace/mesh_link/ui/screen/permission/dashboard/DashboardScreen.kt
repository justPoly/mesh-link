package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController

import com.orliczspace.mesh_link.ui.navigation.Routes
import com.orliczspace.mesh_link.ui.scaffold.MeshScaffold

@Composable
fun DashboardScreen(
    navController: NavHostController
) {

    MeshScaffold(
        title = "Dashboard",
        navController = navController,
        currentRoute = Routes.Dashboard.route
    ) { padding ->

        DashboardContent(padding)

    }
}

@Composable
private fun DashboardContent(
    padding: PaddingValues
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {

        // We'll build the real dashboard here later.

    }

}