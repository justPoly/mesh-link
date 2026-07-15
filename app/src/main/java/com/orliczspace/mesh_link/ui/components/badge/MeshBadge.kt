package com.orliczspace.mesh_link.ui.components.badge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MeshBadge(

    text: String

) {

    Text(

        text = text,

        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primary,
                CircleShape
            )
            .padding(
                horizontal = 12.dp,
                vertical = 6.dp
            ),

        color = MaterialTheme.colorScheme.onPrimary

    )

}