package com.orliczspace.mesh_link.ui.navigation

sealed class Routes(val route: String) {

    data object Dashboard : Routes("dashboard")

    data object Discovery : Routes("discovery")

    data object Nodes : Routes("nodes")

    data object Topology : Routes("topology")

    data object Routing : Routes("routing")

    data object Performance : Routes("performance")

    data object Settings : Routes("settings")
}