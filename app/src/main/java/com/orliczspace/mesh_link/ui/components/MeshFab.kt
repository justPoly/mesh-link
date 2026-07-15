package com.orliczspace.mesh_link.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
fun MeshFab(

    onClick: () -> Unit

) {

    ExtendedFloatingActionButton(

        onClick = onClick,

        icon = {

            Icon(Icons.Default.Wifi, null)

        },

        text = {

            Text("Discover")

        }

    )

}