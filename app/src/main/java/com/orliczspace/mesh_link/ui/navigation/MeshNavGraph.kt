package com.orliczspace.mesh_link.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.orliczspace.mesh_link.ui.screen.dashboard.DashboardScreen
import com.orliczspace.mesh_link.ui.screen.discovery.NodeDiscoveryScreen
import com.orliczspace.mesh_link.ui.screen.nodes.AvailableNodesScreen
import com.orliczspace.mesh_link.ui.screen.performance.PerformanceScreen
import com.orliczspace.mesh_link.ui.screen.routing.BestRouteScreen
import com.orliczspace.mesh_link.ui.screen.settings.SettingsScreen
import com.orliczspace.mesh_link.ui.screen.topology.TopologyScreen
import com.orliczspace.mesh_link.ui.screen.connected.ConnectedScreen

@Composable
fun MeshNavGraph() {

    val navController = rememberNavController()

    Scaffold(

        bottomBar = {

            BottomBar(navController)

        }

    ) { padding ->

        NavHost(

            navController = navController,

            startDestination = Routes.Dashboard.route,

            modifier = Modifier.padding(padding)

        ) {

            composable(Routes.Dashboard.route) {

                DashboardScreen()

            }

            composable(Routes.Discovery.route) {

                NodeDiscoveryScreen()

            }

            composable(Routes.Nodes.route) {

                AvailableNodesScreen()

            }

            composable(Routes.Topology.route) {

                TopologyScreen()

            }

            composable(Routes.Routing.route) {

                BestRouteScreen()

            }

            composable(Routes.Connected.route) {

                ConnectedScreen()

            }

            composable(Routes.Performance.route) {

                PerformanceScreen()

            }

            composable(Routes.Settings.route) {

                SettingsScreen()

            }

        }

    }

}