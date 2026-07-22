package com.orliczspace.mesh_link.ui.mapper

import com.orliczspace.mesh_link.network.RoutingState

fun List<RoutingState>.toUiNodes() =
    map { it.toUiNode() }

fun List<RoutingState>.toUiRoutes() =
    map { it.toUiRoute() }

fun List<RoutingState>.toUiGateways() =
    filter { it.isGateway }
        .map { it.toUiGateway() }