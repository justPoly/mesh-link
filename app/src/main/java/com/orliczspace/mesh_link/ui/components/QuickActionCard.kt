package com.orliczspace.mesh_link.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.orliczspace.mesh_link.ui.components.button.MeshButton

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