package com.orliczspace.mesh_link.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshTopBar(

    title: String,

    onProfile: () -> Unit = {},

    onNotification: () -> Unit = {}

) {

    TopAppBar(

        title = {

            Text(title)

        },

        actions = {

            IconButton(onClick = onNotification) {

                Icon(Icons.Default.Notifications, null)

            }

            IconButton(onClick = onProfile) {

                Icon(Icons.Default.Person, null)

            }

        }

    )

}