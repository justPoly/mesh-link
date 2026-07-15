package com.orliczspace.mesh_link.ui.components.card

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.ui.components.button.MeshButton

@Composable
fun PermissionCard(

    onGrantPermission: () -> Unit

) {

    DashboardCard {

        Text(
            "Permissions Required",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "MeshLink needs nearby device and location permissions."
        )

        Spacer(Modifier.height(24.dp))

        MeshButton(

            text = "Grant Permission",

            onClick = onGrantPermission

        )

    }

}