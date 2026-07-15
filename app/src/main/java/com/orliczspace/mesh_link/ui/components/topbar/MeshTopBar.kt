package com.orliczspace.mesh_link.ui.components.topbar

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshTopBar(

    title: String

) {

    CenterAlignedTopAppBar(

        title = {

            Text(

                text = title,

                style = MaterialTheme.typography.titleLarge

            )

        }

    )

}