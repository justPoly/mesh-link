package com.orliczspace.mesh_link.ui.screen.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer

import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import androidx.navigation.NavHostController

import com.orliczspace.mesh_link.network.MeshReachabilityState
import com.orliczspace.mesh_link.network.PeerConnectionState
import com.orliczspace.mesh_link.ui.navigation.MESH_DIAGNOSTICS_ROUTE
import com.orliczspace.mesh_link.ui.scaffold.MeshScaffold

import kotlinx.coroutines.delay

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MeshDiagnosticsScreen(

    navController: NavHostController,

    viewModel: MeshDiagnosticsViewModel

) {

    val context = LocalContext.current

    val state by viewModel.uiState.collectAsState()

    // Refresh while this screen is visible.
    // The coroutine is cancelled when the screen leaves
    // composition.

    LaunchedEffect(viewModel) {

        while (true) {

            viewModel.refresh()

            delay(1_000L)
        }
    }

    MeshScaffold(

        title = "Mesh Diagnostics",

        navController = navController,

        currentRoute = MESH_DIAGNOSTICS_ROUTE

    ) { padding ->

        LazyColumn(

            modifier = Modifier
                .fillMaxSize()
                .padding(padding),

            contentPadding = PaddingValues(16.dp),

            verticalArrangement =
                Arrangement.spacedBy(16.dp)

        ) {

            item {

                TextButton(
                    onClick = {
                        navController.popBackStack()
                    }
                ) {
                    Text("Back")
                }
            }

            item {

                DiagnosticsHeader()
            }

            item {

                LocalNodeCard(

                    state = state,

                    onCopyNodeId = {

                        copyText(
                            context = context,
                            label = "Mesh-Link Node ID",
                            text = state.localNodeId
                        )
                    }
                )
            }

            item {

                NetworkOverviewCard(state)
            }

            item {

                DiagnosticStatisticsCard(state)
            }

            item {

                DiagnosticActions(

                    hasConnectedPeers =
                        state.connectedPeerCount > 0,

                    onDiscover = {

                        viewModel.requestDiscovery()
                    },

                    onTestAll = {

                        viewModel.testAllPeers()
                    }
                )
            }

            item {

                Text(

                    text = "Peer Diagnostics",

                    style =
                        MaterialTheme.typography.titleLarge
                )
            }

            if (state.peers.isEmpty()) {

                item {

                    NoPeersCard()
                }

            } else {

                items(

                    items = state.peers,

                    key = { it.peer.nodeId }

                ) { diagnostic ->

                    PeerDiagnosticsCard(

                        diagnostic = diagnostic,

                        currentTime = state.lastUpdatedAt,

                        onTestPeer = {

                            val started =
                                viewModel.testPeer(
                                    diagnostic.peer.nodeId
                                )

                            if (!started) {

                                Toast.makeText(
                                    context,
                                    "Test unavailable or already running",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }

            item {

                DiagnosticReportCard(

                    onCopyReport = {

                        copyText(

                            context = context,

                            label =
                                "Mesh-Link Diagnostic Report",

                            text =
                                viewModel.generateReport()
                        )
                    },

                    onShareReport = {

                        val report =
                            viewModel.generateReport()

                        val intent =
                            Intent(Intent.ACTION_SEND).apply {

                                type = "text/plain"

                                putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    "Mesh-Link Diagnostic Report"
                                )

                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    report
                                )
                            }

                        context.startActivity(

                            Intent.createChooser(
                                intent,
                                "Share diagnostic report"
                            )
                        )
                    }
                )
            }

            item {

                Text(

                    text = "Last refreshed: ${
                        formatTimestamp(
                            state.lastUpdatedAt
                        )
                    }",

                    style =
                        MaterialTheme.typography.labelSmall,

                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Diagnostics header
// -------------------------------------------------------------

@Composable
private fun DiagnosticsHeader() {

    Card(

        modifier = Modifier.fillMaxWidth(),

        colors = CardDefaults.cardColors(

            containerColor =
                MaterialTheme.colorScheme.primaryContainer
        )

    ) {

        Column(

            modifier = Modifier.padding(16.dp),

            verticalArrangement =
                Arrangement.spacedBy(8.dp)

        ) {

            Text(

                text = "Mesh Network Diagnostics",

                style =
                    MaterialTheme.typography.titleLarge
            )

            Text(

                text = "Local diagnostic mode",

                style =
                    MaterialTheme.typography.titleSmall
            )

            Text(

                text = "Monitor peer discovery, heartbeat, " +
                        "routing and direct mesh transport " +
                        "reachability.",

                style =
                    MaterialTheme.typography.bodyMedium
            )

            Text(

                text = "VPN and remote internet forwarding " +
                        "are disabled for this testing phase.",

                style =
                    MaterialTheme.typography.bodySmall
            )
        }
    }
}

// -------------------------------------------------------------
// Local node information
// -------------------------------------------------------------

@Composable
private fun LocalNodeCard(

    state: MeshDiagnosticsUiState,

    onCopyNodeId: () -> Unit

) {

    DiagnosticCard(title = "Local Node") {

        Text(

            text = state.deviceName,

            style =
                MaterialTheme.typography.titleMedium
        )

        Text(

            text = "Persistent Node ID",

            style =
                MaterialTheme.typography.labelMedium
        )

        SelectionContainer {

            Text(

                text = state.localNodeId,

                style =
                    MaterialTheme.typography.bodyMedium
            )
        }

        OutlinedButton(

            onClick = onCopyNodeId,

            modifier = Modifier.fillMaxWidth()

        ) {

            Text("Copy Node ID")
        }

        Divider()

        DiagnosticRow(

            label = "Direct internet",

            value =
                if (state.hasDirectInternet)
                    "Available"
                else
                    "Unavailable",

            valueColor =
                if (state.hasDirectInternet)
                    Color(0xFF2E7D32)
                else
                    MaterialTheme.colorScheme.error
        )

        DiagnosticRow(

            label = "Discovery port",

            value = state.discoveryPort.toString()
        )

        DiagnosticRow(

            label = "Mesh transport port",

            value = state.meshPort.toString()
        )
    }
}

// -------------------------------------------------------------
// Network overview
// -------------------------------------------------------------

@Composable
private fun NetworkOverviewCard(

    state: MeshDiagnosticsUiState

) {

    DiagnosticCard(title = "Network Overview") {

        Row(

            modifier = Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.spacedBy(12.dp)

        ) {

            MetricTile(

                modifier = Modifier.weight(1f),

                label = "Discovered",

                value =
                    state.discoveredPeerCount.toString()
            )

            MetricTile(

                modifier = Modifier.weight(1f),

                label = "Connected",

                value =
                    state.connectedPeerCount.toString()
            )
        }

        Row(

            modifier = Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.spacedBy(12.dp)

        ) {

            MetricTile(

                modifier = Modifier.weight(1f),

                label = "Reachable",

                value =
                    state.reachablePeerCount.toString()
            )

            MetricTile(

                modifier = Modifier.weight(1f),

                label = "Lost",

                value =
                    state.lostPeerCount.toString()
            )
        }

        Divider()

        DiagnosticRow(

            label = "Total known peers",

            value = state.peers.size.toString()
        )

        DiagnosticRow(

            label = "Registered endpoints",

            value =
                state.registeredEndpointCount.toString()
        )

        DiagnosticRow(

            label = "Routing entries",

            value =
                state.routingEntryCount.toString()
        )
    }
}

// -------------------------------------------------------------
// Overall diagnostic statistics
// -------------------------------------------------------------

@Composable
private fun DiagnosticStatisticsCard(

    state: MeshDiagnosticsUiState

) {

    DiagnosticCard(title = "Mesh Transport Statistics") {

        Row(

            modifier = Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.spacedBy(8.dp)

        ) {

            MetricTile(

                modifier = Modifier.weight(1f),

                label = "Sent",

                value = state.totalTestsSent.toString()
            )

            MetricTile(

                modifier = Modifier.weight(1f),

                label = "Passed",

                value =
                    state.totalTestsSuccessful.toString()
            )

            MetricTile(

                modifier = Modifier.weight(1f),

                label = "Failed",

                value =
                    state.totalTestsFailed.toString()
            )
        }

        DiagnosticRow(

            label = "Round-trip failure rate",

            value = formatPercent(
                state.diagnosticFailureRate
            )
        )

        Text(

            text = "Statistics are collected from local " +
                    "mesh diagnostic tests during this session.",

            style =
                MaterialTheme.typography.bodySmall,

            color =
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// -------------------------------------------------------------
// Diagnostic actions
// -------------------------------------------------------------

@Composable
private fun DiagnosticActions(

    hasConnectedPeers: Boolean,

    onDiscover: () -> Unit,

    onTestAll: () -> Unit

) {

    DiagnosticCard(title = "Network Actions") {

        Button(

            onClick = onDiscover,

            modifier = Modifier.fillMaxWidth()

        ) {

            Text("Discover Nearby Nodes")
        }

        OutlinedButton(

            onClick = onTestAll,

            enabled = hasConnectedPeers,

            modifier = Modifier.fillMaxWidth()

        ) {

            Text("Test All Connected Peers")
        }
    }
}

// -------------------------------------------------------------
// Individual peer diagnostics
// -------------------------------------------------------------

@Composable
private fun PeerDiagnosticsCard(

    diagnostic: PeerDiagnosticUiState,

    currentTime: Long,

    onTestPeer: () -> Unit

) {

    val peer = diagnostic.peer

    val isConnected =
        peer.connectionState ==
                PeerConnectionState.CONNECTED

    val isTesting =
        peer.meshReachability ==
                MeshReachabilityState.TESTING

    DiagnosticCard(title = peer.deviceName) {

        Text(

            text = "${peer.ipAddress}:${peer.meshPort}",

            style =
                MaterialTheme.typography.bodyMedium
        )

        Text(

            text = when (peer.connectionState) {

                PeerConnectionState.CONNECTED ->
                    "Connected"

                PeerConnectionState.DISCOVERED ->
                    "Discovered"

                PeerConnectionState.LOST ->
                    "Lost"
            },

            style =
                MaterialTheme.typography.titleSmall,

            color =
                connectionStateColor(
                    peer.connectionState
                )
        )

        Text(

            text = "Node ID",

            style =
                MaterialTheme.typography.labelSmall
        )

        SelectionContainer {

            Text(

                text = peer.nodeId,

                style =
                    MaterialTheme.typography.bodySmall
            )
        }

        Divider()

        DiagnosticRow(

            label = "Discovery latency",

            value =
                peer.latencyMs?.let { "$it ms" }
                    ?: "Unavailable"
        )

        DiagnosticRow(

            label = "Mesh latency",

            value =
                peer.meshLatencyMs?.let { "$it ms" }
                    ?: "Unavailable"
        )

        DiagnosticRow(

            label = "Mesh reachability",

            value = when (peer.meshReachability) {

                MeshReachabilityState.NOT_TESTED ->
                    "Not tested"

                MeshReachabilityState.TESTING ->
                    "Testing"

                MeshReachabilityState.REACHABLE ->
                    "Reachable"

                MeshReachabilityState.UNREACHABLE ->
                    "Unreachable"
            },

            valueColor =
                reachabilityColor(
                    peer.meshReachability
                )
        )

        DiagnosticRow(

            label = "Last seen",

            value = formatLastSeen(
                lastSeen = peer.lastSeen,
                currentTime = currentTime
            )
        )

        DiagnosticRow(

            label = "RSSI",

            value =
                peer.rssi?.let { "$it dBm" }
                    ?: "Unavailable"
        )

        DiagnosticRow(

            label = "Internet capability",

            value =
                if (!isConnected)
                    "Not verified"
                else if (peer.hasInternetAccess)
                    "Available"
                else
                    "Unavailable"
        )

        Divider()

        DiagnosticRow(

            label = "Forwarding endpoint",

            value =
                if (diagnostic.hasRegisteredEndpoint)
                    "Registered"
                else
                    "Not registered",

            valueColor =
                if (diagnostic.hasRegisteredEndpoint)
                    Color(0xFF2E7D32)
                else
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
        )

        DiagnosticRow(

            label = "Routing entry",

            value =
                if (diagnostic.hasRoutingEntry)
                    "Available"
                else
                    "Not available"
        )

        DiagnosticRow(

            label = "Gateway selected",

            value =
                if (diagnostic.isGateway)
                    "Yes"
                else
                    "No"
        )

        Divider()

        Row(

            modifier = Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.spacedBy(8.dp)

        ) {

            MetricTile(

                modifier = Modifier.weight(1f),

                label = "Sent",

                value =
                    peer.meshTestsSent.toString()
            )

            MetricTile(

                modifier = Modifier.weight(1f),

                label = "Passed",

                value =
                    peer.meshTestsSuccessful.toString()
            )

            MetricTile(

                modifier = Modifier.weight(1f),

                label = "Failed",

                value =
                    peer.meshTestsFailed.toString()
            )
        }

        DiagnosticRow(

            label = "Round-trip failure rate",

            value =
                formatPercent(
                    peer.meshPacketLossPercent
                )
        )

        DiagnosticRow(

            label = "Last mesh test",

            value =
                peer.lastMeshTestAt?.let {

                    formatTimestamp(it)

                } ?: "Not tested"
        )

        Button(

            onClick = onTestPeer,

            enabled =
                isConnected &&
                        diagnostic.hasRegisteredEndpoint &&
                        !isTesting,

            modifier = Modifier.fillMaxWidth()

        ) {

            Text(

                if (isTesting)
                    "Testing connection..."
                else
                    "Test Mesh Connection"
            )
        }

        if (!isConnected) {

            Text(

                text = "Waiting for the peer to establish " +
                        "or restore its connection.",

                style =
                    MaterialTheme.typography.bodySmall,

                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// -------------------------------------------------------------
// Empty peer state
// -------------------------------------------------------------

@Composable
private fun NoPeersCard() {

    DiagnosticCard(title = "No Peers Discovered") {

        Text(

            text = "Connect both smartphones to the same " +
                    "reachable Wi-Fi network and start discovery.",

            style =
                MaterialTheme.typography.bodyMedium
        )

        Text(

            text = "Discovered, connected and lost peers " +
                    "will appear here automatically.",

            style =
                MaterialTheme.typography.bodySmall
        )
    }
}

// -------------------------------------------------------------
// Diagnostic report actions
// -------------------------------------------------------------

@Composable
private fun DiagnosticReportCard(

    onCopyReport: () -> Unit,

    onShareReport: () -> Unit

) {

    DiagnosticCard(title = "Diagnostic Report") {

        Text(

            text = "Collect a snapshot of the current " +
                    "network state for testing and research.",

            style =
                MaterialTheme.typography.bodyMedium
        )

        OutlinedButton(

            onClick = onCopyReport,

            modifier = Modifier.fillMaxWidth()

        ) {

            Text("Copy Diagnostic Report")
        }

        Button(

            onClick = onShareReport,

            modifier = Modifier.fillMaxWidth()

        ) {

            Text("Share Diagnostic Report")
        }

        Text(

            text = "Reports contain node identities and " +
                    "local IP addresses. Share only with " +
                    "authorized recipients.",

            style =
                MaterialTheme.typography.bodySmall
        )
    }
}

// -------------------------------------------------------------
// Reusable diagnostic card
// -------------------------------------------------------------

@Composable
private fun DiagnosticCard(

    title: String,

    content: @Composable () -> Unit

) {

    Card(

        modifier = Modifier.fillMaxWidth()

    ) {

        Column(

            modifier = Modifier.padding(16.dp),

            verticalArrangement =
                Arrangement.spacedBy(12.dp)

        ) {

            Text(

                text = title,

                style =
                    MaterialTheme.typography.titleMedium
            )

            content()
        }
    }
}

// -------------------------------------------------------------
// Reusable metric tile
// -------------------------------------------------------------

@Composable
private fun MetricTile(

    modifier: Modifier = Modifier,

    label: String,

    value: String

) {

    Card(

        modifier = modifier,

        colors = CardDefaults.cardColors(

            containerColor =
                MaterialTheme.colorScheme.surfaceVariant
        )

    ) {

        Column(

            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.spacedBy(4.dp)

        ) {

            Text(

                text = value,

                style =
                    MaterialTheme.typography.headlineSmall
            )

            Text(

                text = label,

                style =
                    MaterialTheme.typography.labelMedium
            )
        }
    }
}

// -------------------------------------------------------------
// Reusable diagnostic information row
// -------------------------------------------------------------

@Composable
private fun DiagnosticRow(

    label: String,

    value: String,

    valueColor: Color =
        MaterialTheme.colorScheme.onSurface

) {

    Row(

        modifier = Modifier.fillMaxWidth(),

        horizontalArrangement =
            Arrangement.spacedBy(12.dp),

        verticalAlignment =
            Alignment.CenterVertically

    ) {

        Text(

            text = label,

            modifier = Modifier.weight(1f),

            style =
                MaterialTheme.typography.bodyMedium,

            color =
                MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(

            text = value,

            style =
                MaterialTheme.typography.bodyMedium,

            color = valueColor
        )
    }
}

// -------------------------------------------------------------
// State colors
// -------------------------------------------------------------

@Composable
private fun connectionStateColor(

    state: PeerConnectionState

): Color {

    return when (state) {

        PeerConnectionState.CONNECTED ->
            Color(0xFF2E7D32)

        PeerConnectionState.DISCOVERED ->
            MaterialTheme.colorScheme.tertiary

        PeerConnectionState.LOST ->
            MaterialTheme.colorScheme.error
    }
}

@Composable
private fun reachabilityColor(

    state: MeshReachabilityState

): Color {

    return when (state) {

        MeshReachabilityState.REACHABLE ->
            Color(0xFF2E7D32)

        MeshReachabilityState.UNREACHABLE ->
            MaterialTheme.colorScheme.error

        MeshReachabilityState.TESTING ->
            MaterialTheme.colorScheme.tertiary

        MeshReachabilityState.NOT_TESTED ->
            MaterialTheme.colorScheme.onSurfaceVariant
    }
}

// -------------------------------------------------------------
// Formatting helpers
// -------------------------------------------------------------

private fun formatLastSeen(

    lastSeen: Long,

    currentTime: Long

): String {

    val seconds =
        ((currentTime - lastSeen) / 1_000L)
            .coerceAtLeast(0L)

    return when {

        seconds < 2 ->
            "Just now"

        seconds < 60 ->
            "$seconds seconds ago"

        seconds < 3_600 ->
            "${seconds / 60} minutes ago"

        else ->
            "${seconds / 3_600} hours ago"
    }
}

private fun formatTimestamp(

    timestamp: Long

): String {

    return SimpleDateFormat(

        "HH:mm:ss",

        Locale.getDefault()

    ).format(Date(timestamp))
}

private fun formatPercent(

    value: Double?

): String {

    return value?.let {

        String.format(
            Locale.US,
            "%.1f%%",
            it
        )

    } ?: "Not available"
}

// -------------------------------------------------------------
// Clipboard helper
// -------------------------------------------------------------

private fun copyText(

    context: Context,

    label: String,

    text: String

) {

    val clipboard =
        context.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager

    clipboard.setPrimaryClip(

        ClipData.newPlainText(
            label,
            text
        )
    )

    Toast.makeText(

        context,

        "$label copied",

        Toast.LENGTH_SHORT

    ).show()
}
