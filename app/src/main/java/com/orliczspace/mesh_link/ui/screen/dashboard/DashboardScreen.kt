package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.orliczspace.mesh_link.network.InternetMonitor
import com.orliczspace.mesh_link.network.LinkProbeService
import com.orliczspace.mesh_link.network.NeighbourDiscoveryService
import com.orliczspace.mesh_link.network.RoutingStateRepository
import com.orliczspace.mesh_link.network.gateway.GatewayNatService
import com.orliczspace.mesh_link.ui.navigation.Routes
import com.orliczspace.mesh_link.ui.scaffold.MeshScaffold
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    navController: NavHostController,
    linkProbeService: LinkProbeService,
    routingRepository: RoutingStateRepository,
    internetMonitor: InternetMonitor,
    neighbourService: NeighbourDiscoveryService,
    gatewayNatService: GatewayNatService
) {
    /*
     * LinkProbeService and GatewayNatService use
     * ConcurrentHashMap internally, so changes inside
     * those services are not automatically observable
     * by Compose.
     *
     * Refresh the dashboard once every second so
     * current networking values are reflected.
     */
    var dashboardRefresh by remember {
        mutableStateOf(0)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            dashboardRefresh++
        }
    }

    /*
     * Make Compose read the latest non-observable
     * networking state on every refresh.
     */
    val refreshKey = dashboardRefresh

    /*
     * Internet status.
     */
    val isInternetConnected =
        internetMonitor.isConnected.value

    val connectionType =
        internetMonitor.connectionType.value

    /*
     * Wi-Fi Direct discovered peers.
     */
    val discoveredNodeCount =
        neighbourService.discoveredPeers.size

    /*
     * Nodes discovered through the UDP probe layer.
     */
    val probeNodes =
        linkProbeService.getKnownPeers()

    /*
     * Nodes currently represented in the routing table.
     */
    val routingNodes =
        routingRepository.routingTable

    /*
     * Prefer routing nodes because they have actual
     * routing state.
     *
     * If routing has not been populated yet, use
     * probe-known nodes.
     *
     * Finally fall back to Wi-Fi Direct discovery.
     */
    val connectedNodes =
        when {
            routingNodes.isNotEmpty() ->
                routingNodes.size

            probeNodes.isNotEmpty() ->
                probeNodes.size

            else ->
                discoveredNodeCount
        }

    /*
     * Routing latency values.
     */
    val routingLatencies =
        routingNodes.values
            .map { it.averageLatencyMs }
            .filter { it >= 0 }

    /*
     * Probe RTT values.
     */
    val probeLatencies =
        probeNodes.keys
            .mapNotNull { nodeId ->
                linkProbeService.getAverageRtt(nodeId)
            }

    /*
     * Average network latency.
     */
    val latency =
        when {
            routingLatencies.isNotEmpty() ->
                routingLatencies.average().toInt()

            probeLatencies.isNotEmpty() ->
                probeLatencies.average().toInt()

            else ->
                0
        }

    /*
     * Number of currently elected gateways.
     */
    val gateways =
        routingNodes.values.count {
            it.isGateway
        }

    /*
     * Internet metric:
     *
     * 1 = Internet available
     * 0 = Internet unavailable
     */
    val internet =
        if (isInternetConnected) {
            1
        } else {
            0
        }

    /*
     * Keep the refresh value referenced so Compose
     * continues to re-read the non-observable service state.
     */
    @Suppress("UNUSED_VARIABLE")
    val currentRefresh =
        refreshKey

    MeshScaffold(
        title = "Dashboard",
        navController = navController,
        currentRoute = Routes.Dashboard.route
    ) { padding ->

        DashboardContent(
            padding = padding,

            isInternetConnected =
                isInternetConnected,

            connectionType =
                connectionType,

            connectedNodes =
                connectedNodes,

            latency =
                latency,

            gateways =
                gateways,

            internet =
                internet,

            neighbourService =
                neighbourService,

            linkProbeService =
                linkProbeService,

            routingRepository =
                routingRepository,

            gatewayNatService =
                gatewayNatService,

            onFindNodesClick = {
                navController.navigate(
                    Routes.Nodes.route
                )
            },

            onDiagnosticsClick = {
                navController.navigate(
                    Routes.Performance.route
                )
            },

            onTopologyClick = {
                navController.navigate(
                    Routes.Topology.route
                )
            },

            onRoutesClick = {
                navController.navigate(
                    Routes.Routing.route
                )
            }
        )
    }
}

@Composable
private fun DashboardContent(
    padding: PaddingValues,
    isInternetConnected: Boolean,
    connectionType: String,
    connectedNodes: Int,
    latency: Int,
    gateways: Int,
    internet: Int,
    neighbourService: NeighbourDiscoveryService,
    linkProbeService: LinkProbeService,
    routingRepository: RoutingStateRepository,
    gatewayNatService: GatewayNatService,
    onFindNodesClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    onTopologyClick: () -> Unit,
    onRoutesClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),

        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 24.dp
        ),

        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        item {
            DashboardHeader()
        }

        item {
            StatusSection(
                isConnected = isInternetConnected,
                connectionType = connectionType
            )
        }

        item {
            MetricsSection(
                connectedNodes = connectedNodes,
                latency = latency,
                gateways = gateways,
                internet = internet
            )
        }

        item {
            NearbyNodesSection(
                neighbourService = neighbourService,
                linkProbeService = linkProbeService,
                routingRepository = routingRepository
            )
        }

        item {
            GatewaySection(
                routingRepository = routingRepository
            )
        }

        item {
            NetworkStatsSection(
                gatewayNatService = gatewayNatService
            )
        }

        item {
            QuickActionsSection(
                onFindNodesClick =
                    onFindNodesClick,

                onDiagnosticsClick =
                    onDiagnosticsClick,

                onTopologyClick =
                    onTopologyClick,

                onRoutesClick =
                    onRoutesClick
            )
        }

        item {
            RecentActivitySection()
        }
    }
}