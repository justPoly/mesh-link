package com.orliczspace.mesh_link.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavController

@Composable
fun MeshBottomBar(
    navController: NavController,
    currentRoute: String?
) {

    NavigationBar {

        val items = listOf(

            Triple(Routes.Dashboard, Icons.Default.Home, "Home"),

            Triple(Routes.Discovery, Icons.Default.Search, "Discover"),

            Triple(Routes.Nodes, Icons.Default.Devices, "Nodes"),

            Triple(Routes.Performance, Icons.Default.Analytics, "Stats"),

            Triple(Routes.Settings, Icons.Default.Settings, "Settings")

        )

        items.forEach { item ->

            NavigationBarItem(

                selected = currentRoute == item.first.route,

                onClick = {

                    navController.navigate(item.first.route) {

                        popUpTo(Routes.Dashboard.route)

                        launchSingleTop = true

                    }

                },

                icon = {

                    Icon(item.second, null)

                },

                label = {

                    Text(item.third)

                }

            )

        }

    }

}