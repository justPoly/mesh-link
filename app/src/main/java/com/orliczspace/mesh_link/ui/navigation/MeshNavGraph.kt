package com.orliczspace.mesh_link.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.orliczspace.mesh_link.network.InternetMonitor
import com.orliczspace.mesh_link.network.LinkProbeService
import com.orliczspace.mesh_link.network.NeighbourDiscoveryService
import com.orliczspace.mesh_link.network.RoutingStateRepository
import com.orliczspace.mesh_link.network.gateway.GatewayNatService
import com.orliczspace.mesh_link.ui.screen.dashboard.DashboardScreen
import com.orliczspace.mesh_link.ui.screen.discovery.NodeDiscoveryScreen
import com.orliczspace.mesh_link.ui.screen.mesh_link.discovery.AvailableNodesScreen
import com.orliczspace.mesh_link.ui.screen.performance.PerformanceScreen
import com.orliczspace.mesh_link.ui.screen.routing.BestRouteScreen
import com.orliczspace.mesh_link.ui.screen.settings.SettingsScreen
import com.orliczspace.mesh_link.ui.screen.topology.TopologyScreen

@Composable
fun MeshNavGraph(
    modifier: Modifier = Modifier,
    linkProbeService: LinkProbeService,
    routingRepository: RoutingStateRepository,
    internetMonitor: InternetMonitor,
    neighbourService: NeighbourDiscoveryService,
    gatewayNatService: GatewayNatService
) {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.Dashboard.route,
        modifier = modifier
    ) {

        composable(Routes.Dashboard.route) {

            DashboardScreen(
                navController = navController,
                linkProbeService = linkProbeService,
                routingRepository = routingRepository,
                internetMonitor = internetMonitor,
                neighbourService = neighbourService,
                gatewayNatService = gatewayNatService
            )
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