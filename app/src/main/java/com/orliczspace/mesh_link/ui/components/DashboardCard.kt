package com.orliczspace.mesh_link.ui.components

import com.orliczspace.mesh_link.ui.theme.MeshDimens
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DashboardCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {

    MeshCard(
        modifier = modifier,
        padding = PaddingValues(MeshDimens.Medium)
    ) {

        Column {

            content()

        }

    }

}