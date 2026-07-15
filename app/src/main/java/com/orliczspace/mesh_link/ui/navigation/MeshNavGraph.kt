package com.orliczspace.mesh_link.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import com.orliczspace.mesh_link.ui.screen.dashboard.DashboardScreen
import com.orliczspace.mesh_link.ui.screen.discovery.NodeDiscoveryScreen
import com.orliczspace.mesh_link.ui.screen.performance.PerformanceScreen
import com.orliczspace.mesh_link.ui.screen.permission.discovery.AvailableNodesScreen
import com.orliczspace.mesh_link.ui.screen.permission.settings.SettingsScreen
import com.orliczspace.mesh_link.ui.screen.permission.topology.TopologyScreen
import com.orliczspace.mesh_link.ui.screen.routing.BestRouteScreen

@Composable
fun MeshNavGraph(
    modifier: Modifier = Modifier
) {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.Dashboard.route,
        modifier = modifier
    ) {

        composable(Routes.Dashboard.route) {
            DashboardScreen(navController)
        }

        composable(Routes.Discovery.route) {
            NodeDiscoveryScreen(navController)
        }

        composable(Routes.Nodes.route) {
            AvailableNodesScreen(navController)
        }

        composable(Routes.Topology.route) {
            TopologyScreen(navController)
        }

        composable(Routes.Routing.route) {
            BestRouteScreen(navController)
        }

        composable(Routes.Performance.route) {
            PerformanceScreen(navController)
        }

        composable(Routes.Settings.route) {
            SettingsScreen(navController)
        }
    }
}