package com.orliczspace.mesh_link.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshTopBar(
    title: String,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null
) {

    CenterAlignedTopAppBar(

        title = {

            Text(title)

        },

        navigationIcon = {

            if (showBack) {

                IconButton(onClick = { onBack?.invoke() }) {

                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null
                    )

                }

            }

        }

    )

}