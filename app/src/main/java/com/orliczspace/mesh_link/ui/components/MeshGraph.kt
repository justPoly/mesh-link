package com.orliczspace.mesh_link.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

@Composable
fun MeshGraph(
    modifier: Modifier = Modifier
) {

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
    ) {

        val center = Offset(size.width / 2, size.height / 2)

        drawCircle(
            color = primary,
            radius = 18f,
            center = center
        )

        drawCircle(
            color = secondary,
            radius = 14f,
            center = Offset(center.x - 180f, center.y - 70f)
        )

        drawCircle(
            color = secondary,
            radius = 14f,
            center = Offset(center.x + 170f, center.y - 90f)
        )

        drawCircle(
            color = secondary,
            radius = 14f,
            center = Offset(center.x - 120f, center.y + 120f)
        )

        drawLine(
            color = primary,
            start = center,
            end = Offset(center.x - 180f, center.y - 70f),
            strokeWidth = 5f
        )

        drawLine(
            color = primary,
            start = center,
            end = Offset(center.x + 170f, center.y - 90f),
            strokeWidth = 5f
        )

        drawLine(
            color = primary,
            start = center,
            end = Offset(center.x - 120f, center.y + 120f),
            strokeWidth = 5f
        )
    }
}