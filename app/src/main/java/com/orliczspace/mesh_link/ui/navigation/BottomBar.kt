package com.orliczspace.mesh_link.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BottomBar(
    navController: NavController
) {

    val items = listOf(

        Triple(
            Routes.Dashboard,
            Icons.Default.Dashboard,
            "Dashboard"
        ),

        Triple(
            Routes.Nodes,
            Icons.Default.Devices,
            "Nodes"
        ),

        Triple(
            Routes.Topology,
            Icons.Default.AccountTree,
            "Topology"
        ),

        Triple(
            Routes.Performance,
            Icons.Default.Speed,
            "Performance"
        ),

        Triple(
            Routes.Settings,
            Icons.Default.Settings,
            "Settings"
        )

    )

    NavigationBar {

        val navBackStack =
            navController.currentBackStackEntryAsState()

        val current =
            navBackStack.value?.destination?.route

        items.forEach { item ->

            NavigationBarItem(

                selected = current == item.first.route,

                onClick = {

                    navController.navigate(item.first.route) {

                        launchSingleTop = true

                        restoreState = true

                        popUpTo(Routes.Dashboard.route) {
                            saveState = true
                        }

                    }

                },

                icon = {

                    Icon(
                        imageVector = item.second,
                        contentDescription = item.third
                    )

                },

                label = {

                    Text(item.third)

                }

            )

        }

    }

}