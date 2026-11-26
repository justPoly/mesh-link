package com.orliczspace.mesh_link.ui.screen.discovery

import android.net.wifi.p2p.WifiP2pDevice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import androidx.navigation.NavHostController

import com.orliczspace.mesh_link.network.LinkProbeService
import com.orliczspace.mesh_link.network.NeighbourDiscoveryService
import com.orliczspace.mesh_link.network.WifiDirectConnectionState
import com.orliczspace.mesh_link.network.WifiDirectConnectionInfo
import com.orliczspace.mesh_link.network.PeerConnectionState
import com.orliczspace.mesh_link.network.MeshPeer

import com.orliczspace.mesh_link.ui.navigation.Routes
import com.orliczspace.mesh_link.ui.navigation.MESH_DIAGNOSTICS_ROUTE
import com.orliczspace.mesh_link.ui.scaffold.MeshScaffold

import kotlinx.coroutines.delay

// -------------------------------------------------------------
// Node discovery screen
// -------------------------------------------------------------

@Composable
fun NodeDiscoveryScreen(
    navController: NavHostController,
    linkProbeService: LinkProbeService,
    neighbourService: NeighbourDiscoveryService
) {

    var refreshTick by remember {
        mutableIntStateOf(0)
    }

    var discoveryMessage by remember {
        mutableStateOf<String?>(null)
    }

    var selectedDeviceAddress by remember {
        mutableStateOf<String?>(null)
    }

    // ---------------------------------------------------------
    // Refresh UDP peer information.
    // ---------------------------------------------------------

    LaunchedEffect(linkProbeService) {

        while (true) {

            refreshTick++

            delay(1_000L)
        }
    }

    // ---------------------------------------------------------
    // Start discovery when the screen opens.
    // ---------------------------------------------------------

    LaunchedEffect(
        linkProbeService,
        neighbourService
    ) {

        try {

            linkProbeService.requestDiscovery()

            neighbourService.startDiscovery()

        } catch (e: Exception) {

            discoveryMessage =
                "Could not start discovery: " +
                        (e.message ?: e.javaClass.simpleName)
        }
    }

    // ---------------------------------------------------------
    // Read UDP peers.
    // ---------------------------------------------------------

    val udpPeers = remember(refreshTick) {

        linkProbeService.getAllPeers()

            .filter {
                it.connectionState !=
                        PeerConnectionState.LOST
            }

            .sortedBy {
                it.deviceName
            }
    }

    // ---------------------------------------------------------
    // Read observable Wi-Fi Direct state.
    // ---------------------------------------------------------

    val wifiDirectDevices =
        neighbourService.discoveredDevices

            .distinctBy {
                it.deviceAddress
            }

            .sortedBy {
                it.deviceName
            }

    val wifiDirectStatus =
        neighbourService.discoveryStatus.toString()

    val connectionState =
        neighbourService.connectionState

    val connectionInfo =
        neighbourService.currentConnectionInfo

    val connectionError =
        neighbourService.connectionError

    // ---------------------------------------------------------
    // Reset selected device after disconnection or failure.
    // ---------------------------------------------------------

    LaunchedEffect(connectionState) {

        if (
            connectionState ==
            WifiDirectConnectionState.DISCONNECTED ||

            connectionState ==
            WifiDirectConnectionState.FAILED
        ) {

            selectedDeviceAddress = null
        }
    }

    // ---------------------------------------------------------
    // Screen layout.
    // ---------------------------------------------------------

    MeshScaffold(

        title = "Discover Nodes",

        navController = navController,

        currentRoute = Routes.Discovery.route

    ) { padding ->

        DiscoveryContent(

            padding = padding,

            udpPeers = udpPeers,

            wifiDirectDevices = wifiDirectDevices,

            wifiDirectStatus = wifiDirectStatus,

            connectionState = connectionState,

            connectionInfo = connectionInfo,

            connectionError = connectionError,

            selectedDeviceAddress =
                selectedDeviceAddress,

            discoveryMessage =
                discoveryMessage,

            // -------------------------------------------------
            // Open Mesh Diagnostics.
            // -------------------------------------------------

            onDiagnosticsClick = {

                navController.navigate(
                    MESH_DIAGNOSTICS_ROUTE
                ) {

                    launchSingleTop = true
                }
            },

            // -------------------------------------------------
            // Request discovery manually.
            // -------------------------------------------------

            onDiscoverClick = {

                discoveryMessage = null

                try {

                    linkProbeService.requestDiscovery()

                    neighbourService.startDiscovery()

                    discoveryMessage =
                        "Discovery requested. " +
                                "Searching for nearby devices."

                } catch (e: Exception) {

                    discoveryMessage =
                        "Discovery could not start: " +
                                (e.message
                                    ?: e.javaClass.simpleName)
                }
            },

            // -------------------------------------------------
            // Connect to a selected Wi-Fi Direct device.
            // -------------------------------------------------

            onConnectClick = { device ->

                selectedDeviceAddress =
                    device.deviceAddress

                discoveryMessage = null

                try {

                    neighbourService.connectToPeer(
                        device
                    )

                    if (
                        neighbourService.connectionState ==
                        WifiDirectConnectionState.CONNECTING
                    ) {

                        discoveryMessage =
                            "Connection requested. " +
                                    "Accept the connection on " +
                                    "the receiving smartphone."
                    }

                } catch (e: Exception) {

                    selectedDeviceAddress = null

                    discoveryMessage =
                        "Connection could not start: " +
                                (e.message
                                    ?: e.javaClass.simpleName)
                }
            }
        )
    }
}

// -------------------------------------------------------------
// Discovery screen content
// -------------------------------------------------------------

@Composable
private fun DiscoveryContent(

    padding: PaddingValues,

    udpPeers: List<MeshPeer>,

    wifiDirectDevices: List<WifiP2pDevice>,

    wifiDirectStatus: String,

    connectionState: WifiDirectConnectionState,

    connectionInfo: WifiDirectConnectionInfo?,

    connectionError: String?,

    selectedDeviceAddress: String?,

    discoveryMessage: String?,

    onDiagnosticsClick: () -> Unit,

    onDiscoverClick: () -> Unit,

    onConnectClick: (WifiP2pDevice) -> Unit

) {

    val connectedUdpCount =
        udpPeers.count {

            it.connectionState ==
                    PeerConnectionState.CONNECTED
        }

    // A single LazyColumn allows the entire screen to scroll.

    LazyColumn(

        modifier = Modifier
            .fillMaxSize()
            .padding(padding),

        contentPadding = PaddingValues(16.dp),

        verticalArrangement =
            Arrangement.spacedBy(12.dp)

    ) {

        // -----------------------------------------------------
        // Screen heading
        // -----------------------------------------------------

        item {

            Text(

                text = "Nearby Mesh Nodes",

                style =
                    MaterialTheme.typography.headlineSmall
            )
        }

        // -----------------------------------------------------
        // UDP peer summary
        // -----------------------------------------------------

        item {

            Text(

                text =
                    "${udpPeers.size} UDP peer(s) discovered",

                style =
                    MaterialTheme.typography.titleMedium
            )

            Text(

                text =
                    "$connectedUdpCount verified connection(s)",

                style =
                    MaterialTheme.typography.bodyMedium,

                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }

        // -----------------------------------------------------
        // Discover nodes button
        // -----------------------------------------------------

        item {

            Button(

                onClick = onDiscoverClick,

                enabled =
                    connectionState !=
                            WifiDirectConnectionState.CONNECTING,

                modifier =
                    Modifier.fillMaxWidth()

            ) {

                Text("Discover Nodes")
            }
        }

        // -----------------------------------------------------
        // Mesh Diagnostics button
        // -----------------------------------------------------

        item {

            OutlinedButton(

                onClick = onDiagnosticsClick,

                modifier =
                    Modifier.fillMaxWidth()

            ) {

                Text("Open Mesh Diagnostics")
            }
        }

        // -----------------------------------------------------
        // Discovery feedback
        // -----------------------------------------------------

        discoveryMessage?.let { message ->

            item {

                Text(

                    text = message,

                    style =
                        MaterialTheme.typography.bodyMedium
                )
            }
        }

        // -----------------------------------------------------
        // Wi-Fi Direct connection information
        // -----------------------------------------------------

        item {

            WifiDirectStatusCard(

                discoveryStatus =
                    wifiDirectStatus,

                connectionState =
                    connectionState,

                connectionInfo =
                    connectionInfo,

                connectionError =
                    connectionError,

                discoveredDeviceCount =
                    wifiDirectDevices.size
            )
        }

        // -----------------------------------------------------
        // Available Wi-Fi Direct devices heading
        // -----------------------------------------------------

        item {

            Text(

                text = "Available Wi-Fi Direct Devices",

                style =
                    MaterialTheme.typography.titleMedium
            )

            Text(

                text =
                    "Select a smartphone running Mesh-Link " +
                            "to establish a direct connection.",

                style =
                    MaterialTheme.typography.bodySmall,

                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }

        // -----------------------------------------------------
        // Empty Wi-Fi Direct discovery state
        // -----------------------------------------------------

        if (wifiDirectDevices.isEmpty()) {

            item {

                Card(

                    modifier =
                        Modifier.fillMaxWidth()

                ) {

                    Text(

                        text =
                            "No nearby Wi-Fi Direct devices found. " +
                                    "Enable Wi-Fi on both smartphones " +
                                    "and request discovery again.",

                        modifier =
                            Modifier.padding(16.dp),

                        style =
                            MaterialTheme.typography.bodyMedium
                    )
                }
            }

        } else {

            // -------------------------------------------------
            // Display discovered Wi-Fi Direct devices.
            // -------------------------------------------------

            items(

                items = wifiDirectDevices,

                key = {
                    "wifi_${it.deviceAddress}"
                }

            ) { device ->

                WifiDirectDeviceCard(

                    device = device,

                    connectionState =
                        connectionState,

                    isSelected =
                        selectedDeviceAddress ==
                                device.deviceAddress,

                    onConnectClick = {

                        onConnectClick(device)
                    }
                )
            }
        }

        // -----------------------------------------------------
        // UDP peers heading
        // -----------------------------------------------------

        item {

            Text(

                text = "Discovered Mesh Peers",

                style =
                    MaterialTheme.typography.titleMedium
            )
        }

        // -----------------------------------------------------
        // Empty UDP peer state
        // -----------------------------------------------------

        if (udpPeers.isEmpty()) {

            item {

                Card(

                    modifier =
                        Modifier.fillMaxWidth()

                ) {

                    Column(

                        modifier =
                            Modifier.padding(16.dp),

                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)

                    ) {

                        Text(

                            text =
                                "No UDP mesh peers discovered",

                            style =
                                MaterialTheme.typography.titleSmall
                        )

                        Text(

                            text =
                                if (
                                    connectionState ==
                                    WifiDirectConnectionState.CONNECTED
                                ) {

                                    "Wi-Fi Direct group established. " +
                                            "Waiting for Mesh-Link " +
                                            "discovery responses."

                                } else {

                                    "Connect to a nearby smartphone " +
                                            "through Wi-Fi Direct, or " +
                                            "join the same reachable " +
                                            "local Wi-Fi network."
                                },

                            style =
                                MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

        } else {

            // -------------------------------------------------
            // Display discovered UDP mesh peers.
            // -------------------------------------------------

            items(

                items = udpPeers,

                key = {
                    "udp_${it.nodeId}"
                }

            ) { peer ->

                UdpMeshPeerCard(
                    peer = peer
                )
            }
        }

        // -----------------------------------------------------
        // Networking information
        // -----------------------------------------------------

        item {

            Card(

                modifier =
                    Modifier.fillMaxWidth(),

                colors =
                    CardDefaults.cardColors(

                        containerColor =
                            MaterialTheme.colorScheme.surfaceVariant
                    )

            ) {

                Column(

                    modifier =
                        Modifier.padding(16.dp),

                    verticalArrangement =
                        Arrangement.spacedBy(6.dp)

                ) {

                    Text(

                        text = "Direct Device Communication",

                        style =
                            MaterialTheme.typography.titleSmall
                    )

                    Text(

                        text =
                            "Wi-Fi Direct does not require an " +
                                    "external Wi-Fi router. Keep mobile " +
                                    "data enabled if you want to retain " +
                                    "your individual internet connection.",

                        style =
                            MaterialTheme.typography.bodySmall
                    )

                    Text(

                        text =
                            "Remote internet forwarding is disabled " +
                                    "during local mesh testing.",

                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Wi-Fi Direct status card
// -------------------------------------------------------------

@Composable
private fun WifiDirectStatusCard(

    discoveryStatus: String,

    connectionState: WifiDirectConnectionState,

    connectionInfo: WifiDirectConnectionInfo?,

    connectionError: String?,

    discoveredDeviceCount: Int

) {

    Card(

        modifier =
            Modifier.fillMaxWidth(),

        colors =
            CardDefaults.cardColors(

                containerColor =
                    MaterialTheme.colorScheme.surfaceVariant
            )

    ) {

        Column(

            modifier =
                Modifier.padding(16.dp),

            verticalArrangement =
                Arrangement.spacedBy(8.dp)

        ) {

            Text(

                text = "Wi-Fi Direct",

                style =
                    MaterialTheme.typography.titleMedium
            )

            Text(

                text =
                    "Discovery status: $discoveryStatus",

                style =
                    MaterialTheme.typography.bodyMedium
            )

            Row(

                verticalAlignment =
                    Alignment.CenterVertically,

                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)

            ) {

                Text(

                    text = "Connection:",

                    style =
                        MaterialTheme.typography.bodyMedium
                )

                Text(

                    text =
                        connectionState.name,

                    color =
                        wifiDirectConnectionColor(
                            connectionState
                        ),

                    style =
                        MaterialTheme.typography.labelLarge
                )
            }

            Text(

                text =
                    "Discovered devices: $discoveredDeviceCount",

                style =
                    MaterialTheme.typography.bodyMedium
            )

            // -------------------------------------------------
            // Connection negotiation progress
            // -------------------------------------------------

            if (
                connectionState ==
                WifiDirectConnectionState.CONNECTING
            ) {

                Row(

                    verticalAlignment =
                        Alignment.CenterVertically,

                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp)

                ) {

                    CircularProgressIndicator(

                        modifier =
                            Modifier.size(20.dp),

                        strokeWidth = 2.dp
                    )

                    Text(

                        text =
                            "Waiting for Wi-Fi Direct connection...",

                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }
            }

            // -------------------------------------------------
            // Established group information
            // -------------------------------------------------

            connectionInfo?.let { info ->

                Text(

                    text =
                        "Role: " +
                                if (info.isGroupOwner) {

                                    "Group Owner"

                                } else {

                                    "Client"
                                },

                    style =
                        MaterialTheme.typography.bodyMedium
                )

                Text(

                    text =
                        "Group owner IP: " +
                                (
                                        info.groupOwnerAddress
                                            .hostAddress
                                            ?: "Unavailable"
                                        ),

                    style =
                        MaterialTheme.typography.bodyMedium
                )

                Text(

                    text =
                        "Wi-Fi Direct group established. " +
                                "Mesh reachability is verified separately.",

                    color =
                        Color(0xFF2E7D32),

                    style =
                        MaterialTheme.typography.bodySmall
                )
            }

            // -------------------------------------------------
            // Connection errors
            // -------------------------------------------------

            connectionError?.let { error ->

                Text(

                    text = error,

                    color =
                        MaterialTheme.colorScheme.error,

                    style =
                        MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Wi-Fi Direct device card
// -------------------------------------------------------------

@Composable
private fun WifiDirectDeviceCard(

    device: WifiP2pDevice,

    connectionState: WifiDirectConnectionState,

    isSelected: Boolean,

    onConnectClick: () -> Unit

) {

    val isConnecting =
        connectionState ==
                WifiDirectConnectionState.CONNECTING

    val isGroupConnected =
        connectionState ==
                WifiDirectConnectionState.CONNECTED

    val deviceStatus =
        wifiDirectDeviceStatus(
            device.status
        )

    val canConnect =
        !isConnecting &&
                !isGroupConnected &&
                device.status != WifiP2pDevice.UNAVAILABLE &&
                device.status != WifiP2pDevice.CONNECTED &&
                device.status != WifiP2pDevice.INVITED

    Card(

        modifier =
            Modifier.fillMaxWidth()

    ) {

        Column(

            modifier =
                Modifier.padding(16.dp),

            verticalArrangement =
                Arrangement.spacedBy(8.dp)

        ) {

            Text(

                text =
                    device.deviceName
                        ?.takeIf { it.isNotBlank() }
                        ?: "Unknown Android Device",

                style =
                    MaterialTheme.typography.titleMedium
            )

            Text(

                text =
                    "Device address: ${device.deviceAddress}",

                style =
                    MaterialTheme.typography.bodySmall,

                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )

            Text(

                text = "Device status: $deviceStatus",

                style =
                    MaterialTheme.typography.bodyMedium
            )

            Text(

                text =
                    "Mesh-Link compatibility not yet verified",

                style =
                    MaterialTheme.typography.bodySmall,

                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )

            Button(

                onClick = onConnectClick,

                enabled = canConnect,

                modifier =
                    Modifier.fillMaxWidth()

            ) {

                when {

                    isConnecting && isSelected -> {

                        Row(

                            verticalAlignment =
                                Alignment.CenterVertically,

                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp)

                        ) {

                            CircularProgressIndicator(

                                modifier =
                                    Modifier.size(16.dp),

                                strokeWidth = 2.dp
                            )

                            Text("Connecting...")
                        }
                    }

                    isGroupConnected &&
                            device.status ==
                            WifiP2pDevice.CONNECTED -> {

                        Text("Connected")
                    }

                    isGroupConnected -> {

                        Text("Group Already Active")
                    }

                    isConnecting -> {

                        Text("Connection In Progress")
                    }

                    device.status ==
                            WifiP2pDevice.INVITED -> {

                        Text("Invitation Pending")
                    }

                    device.status ==
                            WifiP2pDevice.UNAVAILABLE -> {

                        Text("Unavailable")
                    }

                    device.status ==
                            WifiP2pDevice.CONNECTED -> {

                        Text("Already Connected")
                    }

                    else -> {

                        Text("Connect")
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// UDP mesh peer card
// -------------------------------------------------------------

@Composable
private fun UdpMeshPeerCard(
    peer: MeshPeer
) {

    Card(

        modifier =
            Modifier.fillMaxWidth()

    ) {

        Column(

            modifier =
                Modifier.padding(16.dp),

            verticalArrangement =
                Arrangement.spacedBy(6.dp)

        ) {

            Text(

                text = peer.deviceName,

                style =
                    MaterialTheme.typography.titleMedium
            )

            Text(

                text =
                    "Node ID: ${peer.nodeId}",

                style =
                    MaterialTheme.typography.bodySmall
            )

            Text(

                text =
                    "IP address: ${peer.ipAddress}",

                style =
                    MaterialTheme.typography.bodyMedium
            )

            Text(

                text =
                    "Mesh port: ${peer.meshPort}",

                style =
                    MaterialTheme.typography.bodyMedium
            )

            Text(

                text =
                    "Connection: ${peer.connectionState.name}",

                color =
                    if (
                        peer.connectionState ==
                        PeerConnectionState.CONNECTED
                    ) {

                        Color(0xFF2E7D32)

                    } else {

                        MaterialTheme.colorScheme.onSurfaceVariant
                    },

                style =
                    MaterialTheme.typography.labelLarge
            )

            Text(

                text =
                    "Discovery latency: " +
                            (
                                    peer.latencyMs?.let {
                                        "$it ms"
                                    } ?: "Unavailable"
                                    ),

                style =
                    MaterialTheme.typography.bodyMedium
            )

            Text(

                text =
                    "Mesh latency: " +
                            (
                                    peer.meshLatencyMs?.let {
                                        "$it ms"
                                    } ?: "Not measured"
                                    ),

                style =
                    MaterialTheme.typography.bodyMedium
            )

            Text(

                text =
                    "Mesh reachability: " +
                            peer.meshReachability.name,

                style =
                    MaterialTheme.typography.bodyMedium
            )

            Text(

                text =
                    "Internet capability: " +
                            if (peer.hasInternetAccess) {

                                "Available"

                            } else {

                                "Unavailable"
                            },

                style =
                    MaterialTheme.typography.bodyMedium
            )

            Text(

                text = "Discovered via UDP",

                color =
                    MaterialTheme.colorScheme.primary,

                style =
                    MaterialTheme.typography.labelMedium
            )
        }
    }
}

// -------------------------------------------------------------
// Wi-Fi Direct connection state colors
// -------------------------------------------------------------

@Composable
private fun wifiDirectConnectionColor(

    state: WifiDirectConnectionState

): Color {

    return when (state) {

        WifiDirectConnectionState.CONNECTED -> {

            Color(0xFF2E7D32)
        }

        WifiDirectConnectionState.CONNECTING -> {

            Color(0xFFF57C00)
        }

        WifiDirectConnectionState.FAILED -> {

            MaterialTheme.colorScheme.error
        }

        WifiDirectConnectionState.DISCONNECTED -> {

            MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
}

// -------------------------------------------------------------
// Android Wi-Fi Direct device status
// -------------------------------------------------------------

private fun wifiDirectDeviceStatus(
    status: Int
): String {

    return when (status) {

        WifiP2pDevice.AVAILABLE ->
            "Available"

        WifiP2pDevice.INVITED ->
            "Invitation Pending"

        WifiP2pDevice.CONNECTED ->
            "Connected"

        WifiP2pDevice.FAILED ->
            "Connection Failed"

        WifiP2pDevice.UNAVAILABLE ->
            "Unavailable"

        else ->
            "Unknown"
    }
}