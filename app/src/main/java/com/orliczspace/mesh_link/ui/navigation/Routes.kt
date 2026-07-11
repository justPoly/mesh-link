package com.orliczspace.mesh_link.ui.navigation

sealed class Routes(val route: String) {

    data object Splash : Routes("splash")

    data object Welcome : Routes("welcome")

    data object Login : Routes("login")

    data object Permission : Routes("permission")

    data object Dashboard : Routes("dashboard")

    data object Discovery : Routes("discovery")

    data object Nodes : Routes("nodes")

    data object Topology : Routes("topology")

    data object Routing : Routes("routing")

    data object Connected : Routes("connected")

    data object Performance : Routes("performance")

    data object Settings : Routes("settings")
}