package com.orliczspace.mesh_link.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun QuickActionCard(
    title: String,
    onClick: () -> Unit
) {

    MeshButton(
        text = title,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    )

}