package com.orliczspace.mesh_link.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MeshChip(
    text: String,
    color: Color
) {

    Text(

        text = text,

        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = .2f))
            .padding(horizontal = 14.dp, vertical = 8.dp),

        color = color,

        style = MaterialTheme.typography.labelLarge

    )

}