
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
import com.orliczspace.mesh_link.network.PeerDiscoveryStatus
import com.orliczspace.mesh_link.network.RoutingStateRepository
import com.orliczspace.mesh_link.network.gateway.GatewayNatService
import com.orliczspace.mesh_link.network.vpn.MeshVpnService
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
     * ConcurrentHashMap internally, so their changes
     * are not automatically observable by Compose.
     *
     * Refresh the dashboard every second to reflect
     * current networking values.
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

    // Read the latest non-observable service state.
    val refreshKey = dashboardRefresh

    // VPN setup error.
    val vpnErrorMessage =
        MeshVpnService.vpnErrorMessage

    // Internet status.
    val isInternetConnected =
        internetMonitor.isConnected.value

    val connectionType =
        internetMonitor.connectionType.value

    // Wi-Fi Direct discovered peers.
    val discoveredNodeCount =
        neighbourService.discoveredPeers.size

    val discoveryStatus =
        neighbourService.discoveryStatus

    // Nodes discovered through the UDP probe layer.
    val probeNodes =
        linkProbeService.getKnownPeers()

    // Nodes currently represented in the routing table.
    val routingNodes =
        routingRepository.routingTable

    /*
     * Connected-node metric.
     * Preserve the existing dashboard calculation.
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

    // Routing latency values.
    val routingLatencies =
        routingNodes.values
            .map { it.averageLatencyMs }
            .filter { it >= 0 }

    // Probe RTT values.
    val probeLatencies =
        probeNodes.keys.mapNotNull { nodeId ->
            linkProbeService.getAverageRtt(nodeId)
        }

    // Average network latency.
    val latency =
        when {
            routingLatencies.isNotEmpty() ->
                routingLatencies.average().toInt()

            probeLatencies.isNotEmpty() ->
                probeLatencies.average().toInt()

            else ->
                0
        }

    // Number of currently elected gateways.
    val gateways =
        routingNodes.values.count {
            it.isGateway
        }

    /*
     * Internet metric:
     * 1 = Internet available
     * 0 = Internet unavailable
     */
    val internet =
        if (isInternetConnected) 1 else 0

    /*
     * Dashboard loading state.
     * Failed or stopped discovery is not treated
     * as an ongoing loading operation.
     */
    val dashboardLoadingState: DashboardLoadingState? =
        when {
            discoveryStatus ==
                    PeerDiscoveryStatus.INITIALIZING -> {
                DashboardLoadingState.INITIALIZING
            }

            routingNodes.isNotEmpty() &&
                    gateways == 0 -> {
                DashboardLoadingState.SELECTING_GATEWAY
            }

            gateways > 0 &&
                    !isInternetConnected -> {
                DashboardLoadingState.CONNECTING
            }

            routingNodes.isEmpty() &&
                    (
                            discoveredNodeCount > 0 ||
                                    probeNodes.isNotEmpty()
                            ) -> {
                DashboardLoadingState.ESTABLISHING_MESH
            }

            discoveryStatus ==
                    PeerDiscoveryStatus.DISCOVERING &&
                    discoveredNodeCount == 0 -> {
                DashboardLoadingState.DISCOVERING_PEERS
            }

            else -> null
        }

    /*
     * Keep the refresh value referenced so Compose
     * re-reads non-observable service state.
     */
    @Suppress("UNUSED_VARIABLE")
    val currentRefresh = refreshKey

    MeshScaffold(
        title = "Dashboard",
        navController = navController,
        currentRoute = Routes.Dashboard.route
    ) { padding ->

        DashboardContent(
            padding = padding,
            isInternetConnected = isInternetConnected,
            connectionType = connectionType,
            connectedNodes = connectedNodes,
            latency = latency,
            gateways = gateways,
            internet = internet,
            dashboardLoadingState = dashboardLoadingState,
            discoveryStatus = discoveryStatus,
            vpnErrorMessage = vpnErrorMessage,
            neighbourService = neighbourService,
            linkProbeService = linkProbeService,
            routingRepository = routingRepository,
            gatewayNatService = gatewayNatService,

            // Card 12: Discover screen.
            onDiscoverClick = {
                navController.navigate(
                    Routes.Discovery.route
                )
            },

            // Card 12: Nodes screen.
            onNodesClick = {
                navController.navigate(
                    Routes.Nodes.route
                )
            },

            // Card 12: Topology screen.
            onTopologyClick = {
                navController.navigate(
                    Routes.Topology.route
                )
            },

            // Card 12: Routes screen.
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
    dashboardLoadingState: DashboardLoadingState?,
    discoveryStatus: PeerDiscoveryStatus,
    vpnErrorMessage: String?,
    neighbourService: NeighbourDiscoveryService,
    linkProbeService: LinkProbeService,
    routingRepository: RoutingStateRepository,
    gatewayNatService: GatewayNatService,
    onDiscoverClick: () -> Unit,
    onNodesClick: () -> Unit,
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

        // Card 11: VPN setup error.
        item {
            vpnErrorMessage?.let { errorMessage ->
                DashboardErrorState(
                    title = "VPN setup failed",
                    message = errorMessage
                )
            }
        }

        // Card 11: Gateway failure.
        item {
            routingRepository.gatewayErrorMessage?.let { errorMessage ->
                DashboardErrorState(
                    title = "Gateway unavailable",
                    message = errorMessage
                )
            }
        }

        // Card 10: Internet empty state.
        item {
            if (!isInternetConnected) {
                DashboardEmptyState(
                    title = "No Internet connection",
                    message = "Internet connectivity is not currently detected. Check your device connection or the mesh gateway."
                )
            }
        }

        // Dashboard loading state.
        item {
            dashboardLoadingState?.let { state ->
                DashboardLoadingSection(
                    state = state
                )
            }
        }

        // Card 11: Route failure.
        item {
            routingRepository.routeErrorMessage?.let { errorMessage ->
                DashboardErrorState(
                    title = "Route failure",
                    message = errorMessage
                )
            }
        }

        // Card 11: Peer discovery error.
        item {
            if (
                discoveryStatus ==
                PeerDiscoveryStatus.FAILED
            ) {
                DashboardErrorState(
                    title = "Peer discovery failed",
                    message = "Mesh-link could not start or continue Wi-Fi Direct discovery. Check Wi-Fi, nearby-device permissions, and location permissions where required, then try discovery again."
                )
            }
        }

        item {
            MetricsSection(
                connectedNodes = connectedNodes,
                latency = latency,
                gateways = gateways,
                internet = internet
            )
        }

        // Card 10: Routing empty state.
        item {
            if (
                routingRepository.routingTable.isEmpty() &&
                dashboardLoadingState == null
            ) {
                DashboardEmptyState(
                    title = "No routes available",
                    message = "The routing table is empty. Discover peers and establish mesh connections to make routes available."
                )
            }
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

        // Card 12: Functional dashboard actions.
        item {
            QuickActionsSection(
                onDiscoverClick = onDiscoverClick,
                onNodesClick = onNodesClick,
                onTopologyClick = onTopologyClick,
                onRoutesClick = onRoutesClick
            )
        }

        item {
            RecentActivitySection()
        }
    }
}
