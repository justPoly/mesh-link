package com.orliczspace.mesh_link.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PermissionRequiredScreen(
    onRequestPermission: () -> Unit
) {

    Surface(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                "Permissions Required",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "MeshLink needs permission to discover nearby devices."
            )

            Spacer(Modifier.height(24.dp))

            Button(onClick = onRequestPermission) {

                Text("Grant Permissions")
            }
        }
    }
}