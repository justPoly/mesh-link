package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.ui.components.*

@Composable
fun DashboardScreen() {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {

        DashboardHeader()

        Spacer(Modifier.height(24.dp))

        StatusCard(
            status = "Connected",
            online = true
        )

        Spacer(Modifier.height(24.dp))

        QuickActionsSection()

        Spacer(Modifier.height(24.dp))

        MetricsSection()

        Spacer(Modifier.height(24.dp))

        NearbyNodesSection()

    }

}