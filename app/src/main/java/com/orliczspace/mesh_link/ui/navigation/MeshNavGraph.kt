
package com.orliczspace.mesh_link.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import com.orliczspace.mesh_link.network.InternetMonitor
import com.orliczspace.mesh_link.network.LinkProbeService
import com.orliczspace.mesh_link.network.MeshReachabilityService
import com.orliczspace.mesh_link.network.NeighbourDiscoveryService
import com.orliczspace.mesh_link.network.PacketForwarder
import com.orliczspace.mesh_link.network.PeerRegistry
import com.orliczspace.mesh_link.network.RoutingStateRepository

import com.orliczspace.mesh_link.network.gateway.GatewayNatService

import com.orliczspace.mesh_link.ui.screen.dashboard.DashboardScreen

import com.orliczspace.mesh_link.ui.screen.diagnostics.MeshDiagnosticsScreen
import com.orliczspace.mesh_link.ui.screen.diagnostics.MeshDiagnosticsViewModel

import com.orliczspace.mesh_link.ui.screen.discovery.NodeDiscoveryScreen

import com.orliczspace.mesh_link.ui.screen.mesh_link.discovery.AvailableNodesScreen

import com.orliczspace.mesh_link.ui.screen.performance.PerformanceScreen
import com.orliczspace.mesh_link.ui.screen.routing.BestRouteScreen
import com.orliczspace.mesh_link.ui.screen.settings.SettingsScreen
import com.orliczspace.mesh_link.ui.screen.topology.TopologyScreen

const val MESH_DIAGNOSTICS_ROUTE =
    "mesh_diagnostics"

@Composable
fun MeshNavGraph(

    modifier: Modifier = Modifier,

    linkProbeService: LinkProbeService,

    routingRepository: RoutingStateRepository,

    internetMonitor: InternetMonitor,

    neighbourService: NeighbourDiscoveryService,

    gatewayNatService: GatewayNatService,

    localNodeId: String,

    peerRegistry: PeerRegistry,

    packetForwarder: PacketForwarder,

    meshReachabilityService: MeshReachabilityService

) {

    val navController =
        rememberNavController()

    // Reuse the existing networking components.
    // Do not create additional service instances.

    val diagnosticsViewModel = remember(

        localNodeId,

        peerRegistry,

        packetForwarder,

        routingRepository,

        internetMonitor,

        linkProbeService,

        meshReachabilityService

    ) {

        MeshDiagnosticsViewModel(

            localNodeId = localNodeId,

            peerRegistry = peerRegistry,

            packetForwarder = packetForwarder,

            routingRepository = routingRepository,

            internetMonitor = internetMonitor,

            linkProbeService = linkProbeService,

            meshReachabilityService =
                meshReachabilityService
        )
    }

    NavHost(

        navController = navController,

        startDestination =
            Routes.Dashboard.route,

        modifier = modifier

    ) {

        // -----------------------------------------------------
        // Dashboard
        // -----------------------------------------------------

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

        // -----------------------------------------------------
        // Discover nodes
        // -----------------------------------------------------

        composable(Routes.Discovery.route) {

            NodeDiscoveryScreen(

                navController = navController,

                linkProbeService = linkProbeService,

                neighbourService = neighbourService
            )
        }

        // -----------------------------------------------------
        // Available nodes
        // -----------------------------------------------------

        composable(Routes.Nodes.route) {

            AvailableNodesScreen(navController)
        }

        // -----------------------------------------------------
        // Topology
        // -----------------------------------------------------

        composable(Routes.Topology.route) {

            TopologyScreen(navController)
        }

        // -----------------------------------------------------
        // Routing
        // -----------------------------------------------------

        composable(Routes.Routing.route) {

            BestRouteScreen(navController)
        }

        // -----------------------------------------------------
        // Performance
        // -----------------------------------------------------

        composable(Routes.Performance.route) {

            PerformanceScreen(navController)
        }

        // -----------------------------------------------------
        // Settings
        // -----------------------------------------------------

        composable(Routes.Settings.route) {

            SettingsScreen(navController)
        }

        // -----------------------------------------------------
        // Mesh diagnostics
        // -----------------------------------------------------

        composable(MESH_DIAGNOSTICS_ROUTE) {

            MeshDiagnosticsScreen(

                navController = navController,

                viewModel = diagnosticsViewModel
            )
        }
    }
}
