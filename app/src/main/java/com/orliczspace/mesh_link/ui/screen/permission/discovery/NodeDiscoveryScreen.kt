package com.orliczspace.mesh_link.ui.screen.discovery

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun NodeDiscoveryScreen() {

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        Text(
            "Searching for nearby nodes...",
            style = MaterialTheme.typography.headlineSmall
        )
    }
}