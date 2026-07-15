package com.orliczspace.mesh_link.ui.screen.permission.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController

import com.orliczspace.mesh_link.ui.navigation.Routes
import com.orliczspace.mesh_link.ui.scaffold.MeshScaffold

@Composable
fun SettingsScreen(
    navController: NavHostController
) {

    MeshScaffold(
        title = "Settings",
        navController = navController,
        currentRoute = Routes.Settings.route
    ) { padding ->

        SettingsContent(padding)

    }

}

@Composable
private fun SettingsContent(
    padding: PaddingValues
) {

    // Placeholder

}