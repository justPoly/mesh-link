package com.orliczspace.mesh_link.ui.scaffold

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.compose.material3.*

import com.orliczspace.mesh_link.ui.components.MeshFab
import com.orliczspace.mesh_link.ui.components.topbar.MeshTopBar
import com.orliczspace.mesh_link.ui.navigation.MeshBottomBar

@Composable
fun MeshScaffold(

    title: String,

    navController: NavController,

    currentRoute: String?,

    content: @Composable (PaddingValues) -> Unit

) {

    Scaffold(

        topBar = {

            MeshTopBar(title)

        },

        bottomBar = {

            MeshBottomBar(

                navController,

                currentRoute

            )

        },

        floatingActionButton = {

            MeshFab {

            }

        }

    ) {

        content(it)

    }

}