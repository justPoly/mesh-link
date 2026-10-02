package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.runtime.Composable

@Composable
fun MetricsSection(
    connectedNodes: Int,
    latency: Int,
    gateways: Int,
    internet: Int
) {
    MetricsGrid(
        connectedNodes = connectedNodes,
        latency = latency,
        gateways = gateways,
        internet = internet
    )
}