package com.orliczspace.mesh_link.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MeshBadge(

    color: Color,

    size: Int = 10

) {

    Box(

        modifier = Modifier
            .size(size.dp)
            .background(color, CircleShape)

    )

}