package com.orliczspace.mesh_link.ui.navigation

sealed class Routes(val route: String) {

    object Dashboard : Routes("dashboard")

    object Discovery : Routes("discovery")

    object Nodes : Routes("nodes")

    object Topology : Routes("topology")

    object Routing : Routes("routing")

    object Connected : Routes("connected")

    object Performance : Routes("performance")

    object Settings : Routes("settings")
}